package com.ironoak.domain;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Customer-facing references (V14), like the order number: PREFIX-YYYYMMDD-XXXXXXXX
 * with 8 random characters (no 0/O/1/I/L). Unique by constraint; the database id
 * stays internal.
 */
public final class ReferenceNumbers {

    public static final String BOOKING_PREFIX = "SB";
    public static final String CLAIM_PREFIX = "WC";
    public static final String TICKET_PREFIX = "ST";

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int SUFFIX_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReferenceNumbers() {
    }

    public static String generate(String prefix) {

        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String date = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZoneOffset.UTC));
        return prefix + "-" + date + "-" + suffix;
    }
}
