package com.ironoak.services;

import com.ironoak.exceptions.InvalidOrderException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Pure unit test of the shipping arithmetic: no Spring, no database. */
class ShippingCalculatorTest {

    private final ShippingCalculator calculator = new ShippingCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void toolsFollowTheDomesticAndInternationalFormulas() {
        // 0.215*distance + 0.275*kg + 17.5*m3 + 2.5, distance in units of 100 km
        assertThat(calculator.toolsCost(true, d("240"), d("2"), d("0.01")).doubleValue())
                .isCloseTo(0.215 * 2.4 + 0.275 * 2 + 17.5 * 0.01 + 2.5, within(0.005));
        // 0.27*distance + 0.25*kg + 15*m3 + 7.5
        assertThat(calculator.toolsCost(false, d("1500"), d("2"), d("0.01")).doubleValue())
                .isCloseTo(0.27 * 15 + 0.25 * 2 + 15 * 0.01 + 7.5, within(0.005));
        assertThat(calculator.toolsCost(true, d("0"), d("0"), d("0"))).isEqualByComparingTo("2.50");
        assertThat(calculator.toolsCost(true, d("0"), d("0"), d("0")).scale()).isEqualTo(2);
    }

    @Test
    void machinesKeepAnIndicativeFormula() {
        assertThat(calculator.machinesCost(true, d("0"), d("150"), d("1.2")).doubleValue())
                .isCloseTo(0.56 * 150 + 2.1 * 1.2 + 48, within(0.005));
    }

    @Test
    void onlySupportedCountriesAreServedAndTheDarienGapCountriesFlyAtAFixedPrice() {
        assertThat(ShippingCalculator.requireSupported(" co ")).isEqualTo("CO");
        assertThatThrownBy(() -> ShippingCalculator.requireSupported("FR")).isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> ShippingCalculator.requireSupported(null)).isInstanceOf(InvalidOrderException.class);
        for (String air : new String[] { "US", "MX", "CR", "PA" }) {
            assertThat(ShippingCalculator.isAir(air)).isTrue();
        }
        for (String road : new String[] { "CO", "EC", "PE", "BR", "AR", "BO" }) {
            assertThat(ShippingCalculator.isAir(road)).isFalse();
        }
        assertThat(ShippingCalculator.airPrice("CO")).isNull();
    }

    @Test
    void theStraightLineEstimateMatchesListedCitiesAndFallsBackOtherwise() {
        assertThat(calculator.distanceKm("CO", "Medellín", null).doubleValue()).isCloseTo(238.7, within(2.0));
        assertThat(calculator.distanceKm("CO", "  CALI ", null).doubleValue()).isCloseTo(300, within(30.0));
        assertThat(calculator.distanceKm("CO", "Nowhere", "Unknown"))
                .isEqualByComparingTo(ShippingCalculator.DOMESTIC_FALLBACK_DISTANCE_KM);
        assertThat(calculator.distanceKm("PE", "Lima", null).doubleValue()).isGreaterThan(1500);
        assertThat(ShippingCalculator.normalize("  Bogotá  D.C. ")).isEqualTo("bogota d c");
    }
}
