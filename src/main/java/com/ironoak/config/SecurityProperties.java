package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds app.security.*: brute-force lockout and request rate limits. The defaults suit a
 * single instance; all counters live in memory (see LoginAttemptTracker).
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /**
     * Trust the last X-Forwarded-For entry as the client address. Enable only behind a reverse
     * proxy that appends it; otherwise anyone can fake their address and dodge every limit.
     */
    private boolean trustForwardedFor = false;

    private final Login login = new Login();
    private final RateLimit rateLimit = new RateLimit();

    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public Login getLogin() {
        return login;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    /** Failed-login lockout. Unknown usernames are counted exactly like real ones. */
    public static class Login {
        /** Failures for one username from one address before that pair is locked. */
        private int maxFailuresPerAccountAndIp = 5;
        /** Failures for one username from anywhere (a distributed guess) before it is locked for everyone. */
        private int maxFailuresPerAccount = 30;
        /** Failures from one address across all usernames (credential stuffing) before it is locked. */
        private int maxFailuresPerIp = 20;
        /** Failures older than this are forgotten. */
        private int windowMinutes = 15;
        /** First lockout; each repeated lockout doubles it up to maxLockoutMinutes. */
        private int lockoutMinutes = 15;
        private int maxLockoutMinutes = 120;
        /** Upper bound on remembered keys, so random usernames cannot exhaust memory. */
        private int maxTrackedKeys = 10_000;

        public int getMaxFailuresPerAccountAndIp() {
            return maxFailuresPerAccountAndIp;
        }

        public void setMaxFailuresPerAccountAndIp(int value) {
            this.maxFailuresPerAccountAndIp = value;
        }

        public int getMaxFailuresPerAccount() {
            return maxFailuresPerAccount;
        }

        public void setMaxFailuresPerAccount(int value) {
            this.maxFailuresPerAccount = value;
        }

        public int getMaxFailuresPerIp() {
            return maxFailuresPerIp;
        }

        public void setMaxFailuresPerIp(int value) {
            this.maxFailuresPerIp = value;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int value) {
            this.windowMinutes = value;
        }

        public int getLockoutMinutes() {
            return lockoutMinutes;
        }

        public void setLockoutMinutes(int value) {
            this.lockoutMinutes = value;
        }

        public int getMaxLockoutMinutes() {
            return maxLockoutMinutes;
        }

        public void setMaxLockoutMinutes(int value) {
            this.maxLockoutMinutes = value;
        }

        public int getMaxTrackedKeys() {
            return maxTrackedKeys;
        }

        public void setMaxTrackedKeys(int value) {
            this.maxTrackedKeys = value;
        }
    }

    /** Requests per minute per client address (token bucket: this is also the burst size). */
    public static class RateLimit {
        private int loginPerMinute = 10;
        private int refreshPerMinute = 30;
        /** Public POSTs: checkout, complaints, bookings, claims, tickets, image classification. */
        private int publicWritePerMinute = 30;
        /** Public lookups by id + email (orders, bookings, claims), which are guessable. */
        private int publicLookupPerMinute = 60;
        private int maxTrackedClients = 10_000;

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int value) {
            this.loginPerMinute = value;
        }

        public int getRefreshPerMinute() {
            return refreshPerMinute;
        }

        public void setRefreshPerMinute(int value) {
            this.refreshPerMinute = value;
        }

        public int getPublicWritePerMinute() {
            return publicWritePerMinute;
        }

        public void setPublicWritePerMinute(int value) {
            this.publicWritePerMinute = value;
        }

        public int getPublicLookupPerMinute() {
            return publicLookupPerMinute;
        }

        public void setPublicLookupPerMinute(int value) {
            this.publicLookupPerMinute = value;
        }

        public int getMaxTrackedClients() {
            return maxTrackedClients;
        }

        public void setMaxTrackedClients(int value) {
            this.maxTrackedClients = value;
        }
    }
}
