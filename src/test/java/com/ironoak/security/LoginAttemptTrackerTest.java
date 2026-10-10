package com.ironoak.security;

import com.ironoak.config.SecurityProperties;
import com.ironoak.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test: no Spring, no database. */
class LoginAttemptTrackerTest {

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MovableClock clock;
    private LoginAttemptTracker tracker;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        SecurityProperties.Login config = new SecurityProperties().getLogin(); // 5 / 30 / 20, 15 min, doubling to 120
        tracker = new LoginAttemptTracker(config, clock);
    }

    private void fail(String user, String ip, int times) {
        for (int i = 0; i < times; i++) {
            tracker.assertNotLocked(user, ip);
            tracker.recordFailure(user, ip);
        }
    }

    @Test
    void locksAnAccountFromOneAddressAfterFiveFailuresWithoutAffectingOtherAddresses() {
        fail("owner", "1.1.1.1", 5);

        assertThatThrownBy(() -> tracker.assertNotLocked("owner", "1.1.1.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 900L));
        // the real owner on another address can still get in
        assertThatCode(() -> tracker.assertNotLocked("owner", "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void unknownUsernamesAreTrackedExactlyLikeRealOnes() {
        fail("no-such-user", "1.1.1.1", 5);
        assertThatThrownBy(() -> tracker.assertNotLocked("no-such-user", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void usernameMatchingIgnoresCaseAndSurroundingSpaces() {
        fail("Owner", "1.1.1.1", 5);
        assertThatThrownBy(() -> tracker.assertNotLocked("  owner ", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aDistributedGuessAtOneAccountLocksItForEveryone() {
        for (int i = 0; i < 30; i++) {
            tracker.recordFailure("owner", "10.0.0." + i); // never more than one failure per address
        }
        assertThatThrownBy(() -> tracker.assertNotLocked("owner", "99.99.99.99"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void credentialStuffingFromOneAddressLocksThatAddress() {
        for (int i = 0; i < 20; i++) {
            tracker.recordFailure("user" + i, "6.6.6.6"); // a different account every time
        }
        assertThatThrownBy(() -> tracker.assertNotLocked("anyone", "6.6.6.6"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> tracker.assertNotLocked("anyone", "7.7.7.7")).doesNotThrowAnyException();
    }

    @Test
    void lockoutEndsAndRepeatOffendersWaitLonger() {
        fail("owner", "1.1.1.1", 5);
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThatCode(() -> tracker.assertNotLocked("owner", "1.1.1.1")).doesNotThrowAnyException();

        fail("owner", "1.1.1.1", 5); // second lockout: 30 minutes
        clock.advance(Duration.ofMinutes(16));
        assertThatThrownBy(() -> tracker.assertNotLocked("owner", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> tracker.assertNotLocked("owner", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void lockoutIsCappedAtTheConfiguredMaximum() {
        for (int round = 0; round < 8; round++) {
            fail("owner", "1.1.1.1", 5);
            clock.advance(Duration.ofMinutes(121));
        }
        fail("owner", "1.1.1.1", 5);
        assertThatThrownBy(() -> tracker.assertNotLocked("owner", "1.1.1.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isLessThanOrEqualTo(120 * 60L));
    }

    @Test
    void oldFailuresAreForgottenAndASuccessClearsTheAccountCounters() {
        fail("owner", "1.1.1.1", 4);
        clock.advance(Duration.ofMinutes(16)); // outside the 15-minute window
        fail("owner", "1.1.1.1", 4);
        assertThatCode(() -> tracker.assertNotLocked("owner", "1.1.1.1")).doesNotThrowAnyException();

        tracker.recordSuccess("owner", "1.1.1.1");
        fail("owner", "1.1.1.1", 4); // would have been the 5th and 9th failure without the reset
        assertThatCode(() -> tracker.assertNotLocked("owner", "1.1.1.1")).doesNotThrowAnyException();
    }
}
