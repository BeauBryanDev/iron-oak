package com.ironoak.security;

import com.ironoak.config.SecurityProperties;
import com.ironoak.exceptions.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Brute-force protection for the login (and the current-password check of a
 * password change).
 *
 * Three independent counters, each with its own threshold and lockout:
 * - username + address: stops guessing one account from one machine without
 * letting an
 * attacker lock the real owner out from another address;
 * - username alone (higher threshold): stops a guess spread over many
 * addresses;
 * - address alone: stops credential stuffing across many usernames.
 *
 * Counting never looks at whether the username exists, so the 429 (and the
 * timing of it) is
 * identical for real and invented accounts and cannot be used to enumerate
 * staff. Lockouts
 * double on repeat up to a ceiling. State is in memory and bounded; with
 * several instances it
 * would need a shared store such as Redis.
 */
@Component
public class LoginAttemptTracker {

    private static final Duration LOCK_COUNT_MEMORY = Duration.ofHours(24);

    private static final class Entry {
        int failures;
        int lockCount;
        Instant lastFailure;
        Instant lockedUntil;
    }

    private final SecurityProperties.Login config;
    private final Clock clock;
    private final Map<String, Entry> pairs;
    private final Map<String, Entry> accounts;
    private final Map<String, Entry> addresses;

    public LoginAttemptTracker(SecurityProperties properties) {

        this(properties.getLogin(), Clock.systemUTC());
    }

    LoginAttemptTracker(SecurityProperties.Login config, Clock clock) {

        this.config = config;
        this.clock = clock;
        this.pairs = boundedMap(config.getMaxTrackedKeys());
        this.accounts = boundedMap(config.getMaxTrackedKeys());
        this.addresses = boundedMap(config.getMaxTrackedKeys());
    }

    /**
     * Throws TooManyRequestsException while any of the three counters is locked.
     */
    public synchronized void assertNotLocked(String username, String ip) {

        Instant now = clock.instant();

        long remaining = Math.max(remaining(pairs.get(pairKey(username, ip)), now),
                Math.max(remaining(accounts.get(accountKey(username)), now),
                        remaining(addresses.get(ip), now)));

        if (remaining > 0) {
            throw new TooManyRequestsException(remaining);
        }
    }

    public synchronized void recordFailure(String username, String ip) {

        Instant now = clock.instant();

        fail(pairs, pairKey(username, ip), config.getMaxFailuresPerAccountAndIp(), now);
        fail(accounts, accountKey(username), config.getMaxFailuresPerAccount(), now);
        fail(addresses, ip, config.getMaxFailuresPerIp(), now);
    }

    /**
     * A good login clears the account counters; the address counter is left to
     * expire on its own.
     */
    public synchronized void recordSuccess(String username, String ip) {
        pairs.remove(pairKey(username, ip));
        accounts.remove(accountKey(username));
    }

    private void fail(Map<String, Entry> map,
            String key,
            int threshold, Instant now) {

        Entry entry = map.computeIfAbsent(key, k -> new Entry());
        boolean locked = entry.lockedUntil != null && entry.lockedUntil.isAfter(now);

        if (entry.lastFailure != null && !locked) {

            Duration quiet = Duration.between(entry.lastFailure, now);

            if (quiet.compareTo(Duration.ofMinutes(config.getWindowMinutes())) > 0) {
                entry.failures = 0;
            }
            if (quiet.compareTo(LOCK_COUNT_MEMORY) > 0) {
                entry.lockCount = 0;
            }
        }
        entry.failures++;
        entry.lastFailure = now;

        if (entry.failures >= threshold) {

            entry.lockCount++;

            long minutes = Math.min(config.getMaxLockoutMinutes(),
                    (long) config.getLockoutMinutes() << Math.min(entry.lockCount - 1, 16));

            entry.lockedUntil = now.plus(Duration.ofMinutes(minutes));
            entry.failures = 0;
        }
    }

    private static long remaining(Entry entry, Instant now) {

        if (entry == null || entry.lockedUntil == null || !entry.lockedUntil.isAfter(now)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, entry.lockedUntil).toSeconds());
    }

    private static String accountKey(String username) {

        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    private static String pairKey(String username, String ip) {

        return accountKey(username) + '\u0000' + ip;
    }

    private static Map<String, Entry> boundedMap(int maxKeys) {

        return new LinkedHashMap<>(16, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > Math.max(1, maxKeys);
            }
        };
    }
}
