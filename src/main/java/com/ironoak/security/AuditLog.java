package com.ironoak.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security events (logins, lockouts, token reuse) go to one logger so they can be shipped
 * or alerted on separately. Attacker-controlled text is cleaned first, so a username with
 * line breaks cannot forge log lines. Passwords and tokens are never passed in.
 */
public final class AuditLog {

    private static final Logger LOG = LoggerFactory.getLogger("com.ironoak.security.audit");

    private AuditLog() {
    }

    public static void info(String event, String username, String ip) {
        LOG.info("{} user={} ip={}", event, clean(username), clean(ip));
    }

    public static void warn(String event, String username, String ip) {
        LOG.warn("{} user={} ip={}", event, clean(username), clean(ip));
    }

    static String clean(String value) {
        if (value == null) {
            return "-";
        }
        String printable = value.replaceAll("[^\\p{Print}]", "?");
        return printable.length() > 64 ? printable.substring(0, 64) + "..." : printable;
    }
}
