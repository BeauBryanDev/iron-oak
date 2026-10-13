package com.ironoak.services;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Turns what a customer types as a country ("Colombia", "México", "USA",
 * "Estados Unidos", "Brasil") into the ISO code the backend uses, for the ten
 * countries Iron & Oak delivers to (ShippingCalculator.SUPPORTED_COUNTRIES).
 * Anything else is returned unchanged, so the shipping rules refuse it with
 * their usual message listing the supported countries.
 */
public final class CountryCodes {

    /** Lower case, no accents, single spaces -> ISO 3166-1 alpha-2. */
    // LLM keeps complainign he cannot use theo rder tool because user country does
    // not match his tool
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("colombia", "CO"),
            Map.entry("republica de colombia", "CO"),
            Map.entry("ecuador", "EC"),
            Map.entry("peru", "PE"),
            Map.entry("brazil", "BR"),
            Map.entry("brasil", "BR"),
            Map.entry("argentina", "AR"),
            Map.entry("bolivia", "BO"),
            Map.entry("panama", "PA"),
            Map.entry("costa rica", "CR"),
            Map.entry("mexico", "MX"),
            Map.entry("united states", "US"),
            Map.entry("united states of america", "US"),
            Map.entry("usa", "US"),
            Map.entry("u s a", "US"),
            Map.entry("u s", "US"),
            Map.entry("us", "US"),
            Map.entry("america", "US"),
            Map.entry("estados unidos", "US"),
            Map.entry("estados unidos de america", "US"),
            Map.entry("eeuu", "US"),
            Map.entry("ee uu", "US"));

    private CountryCodes() {
    }

    /** The ISO code for a country name or code; null and blank stay null. */
    public static String toIsoCode(String country) {

        if (country == null || country.isBlank()) {
            return null;
        }
        String trimmed = country.strip();

        if (trimmed.length() == 2 && trimmed.chars().allMatch(Character::isLetter)) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        String key = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]+", " ")
                .strip();

        return NAMES.getOrDefault(key, trimmed);
    }
}
