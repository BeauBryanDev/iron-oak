package com.ironoak.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxServiceTest {

    private final TaxService taxes = new TaxService();

    @Test
    void taxesItemSubtotalAboveThresholdAtTheCountryRate() {
        assertThat(taxes.taxFor("CO", new BigDecimal("200.00"))).isEqualByComparingTo("38.00");
        assertThat(taxes.taxFor("mx", new BigDecimal("100.01"))).isEqualByComparingTo("26.00");
    }

    @Test
    void noTaxAtOrBelowThresholdOrForUnknownCountry() {
        assertThat(taxes.taxFor("CO", new BigDecimal("100.00"))).isEqualByComparingTo("0");
        assertThat(taxes.taxFor("CL", new BigDecimal("500.00"))).isEqualByComparingTo("0");
        assertThat(taxes.taxFor(null, new BigDecimal("500.00"))).isEqualByComparingTo("0");
    }
}
