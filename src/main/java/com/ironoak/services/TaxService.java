package com.ironoak.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Sales tax per destination country, from classpath taxes.json ({"CO": 0.19,
 * ...}).
 * Only the items are taxed, never shipping, and only when the item subtotal is
 * strictly
 * above TAX_THRESHOLD; a country missing from the file pays no tax.
 */
@Service
public class TaxService {

    /** Orders whose item subtotal is at or below this (USD) are not taxed. */
    public static final BigDecimal TAX_THRESHOLD = new BigDecimal("100.00");

    public static final String TAX_FILE = "taxes.json";

    private final Map<String, BigDecimal> rates;

    public TaxService() {
        try (InputStream in = new ClassPathResource(TAX_FILE).getInputStream()) {
            this.rates = Map.copyOf(new ObjectMapper().readValue(in, new TypeReference<Map<String, BigDecimal>>() {
            }));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + TAX_FILE, e);
        }
    }

    /**
     * The rate for an ISO country code, or zero when the country is unknown or
     * blank.
     */
    public BigDecimal rate(String country) {
        if (country == null || country.isBlank()) {
            return BigDecimal.ZERO;
        }
        return rates.getOrDefault(country.trim().toUpperCase(Locale.ROOT),
                BigDecimal.ZERO);
    }

    /**
     * Tax on the item subtotal, rounded to cents; zero up to and including
     * TAX_THRESHOLD.
     */
    public BigDecimal taxFor(String country,
            BigDecimal itemSubtotal) {

        if (itemSubtotal == null || itemSubtotal.compareTo(TAX_THRESHOLD) <= 0) {

            return BigDecimal.ZERO.setScale(2);

        }
        return itemSubtotal.multiply(rate(country)).setScale(2, RoundingMode.HALF_UP);
    }
}
