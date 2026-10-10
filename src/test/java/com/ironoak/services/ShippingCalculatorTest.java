package com.ironoak.services;

import com.ironoak.exceptions.InvalidOrderException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Pure unit test of the Iron & Oak shipping formulas: no Spring, no database. */
class ShippingCalculatorTest {

    private final ShippingCalculator calculator = new ShippingCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private ShippingCalculator.Quote tools(String country, String city, String kg, String m3) {
        return calculator.quote(country, city, null, true, d(kg), d(m3), false, null, null);
    }

    private ShippingCalculator.Quote machines(String country, String city, String kg, String m3) {
        return calculator.quote(country, city, null, false, null, null, true, d(kg), d(m3));
    }

    @Test
    void internationalToolsFollowTheFormula() {
        var quote = tools("MX", null, "2", "0.01");
        double units = quote.distanceKm().doubleValue() / 100;
        // 0.27*distance + 0.25*weight + 15*volume + 7.5
        double expected = 0.27 * units + 0.25 * 2 + 15 * 0.01 + 7.5;
        assertThat(quote.domestic()).isFalse();
        assertThat(quote.distanceKm().doubleValue()).isCloseTo(3170.7, within(5.0)); // Bogota -> Mexico City
        assertThat(quote.toolsCost().doubleValue()).isCloseTo(expected, within(0.005));
        assertThat(quote.machinesCost()).isEqualByComparingTo("0");
        assertThat(quote.total()).isEqualByComparingTo(quote.toolsCost());
    }

    @Test
    void domesticToolsUseTheColombianFormulaAndCityDistance() {
        var quote = tools("co", "Medellín", "2", "0.01");
        double units = quote.distanceKm().doubleValue() / 100;
        double expected = 0.215 * units + 0.275 * 2 + 17.5 * 0.01 + 2.5;
        assertThat(quote.domestic()).isTrue();
        assertThat(quote.country()).isEqualTo("CO");
        assertThat(quote.distanceKm().doubleValue()).isCloseTo(238.7, within(2.0));
        assertThat(quote.toolsCost().doubleValue()).isCloseTo(expected, within(0.005));
    }

    @Test
    void machinesUseTheirOwnFormulasAndShipSeparatelyFromTools() {
        var intl = machines("US", null, "150", "1.2");
        double units = intl.distanceKm().doubleValue() / 100;
        assertThat(intl.machinesCost().doubleValue())
                .isCloseTo(0.32 * units + 0.46 * 150 + 2.5 * 1.2 + 125, within(0.005));

        var home = machines("CO", "Bogota", "150", "1.2");
        assertThat(home.distanceKm()).isEqualByComparingTo("0");
        assertThat(home.machinesCost().doubleValue()).isCloseTo(0.56 * 150 + 2.1 * 1.2 + 48, within(0.005));

        var both = calculator.quote("BR", null, null, true, d("2"), d("0.01"), true, d("150"), d("1.2"));
        assertThat(both.total()).isEqualByComparingTo(both.toolsCost().add(both.machinesCost()));
        assertThat(both.toolsCost().signum()).isPositive();
        assertThat(both.machinesCost().signum()).isPositive();
    }

    @Test
    void eachGroupPaysItsBaseFeeOnceAndAmountsAreRoundedToCents() {
        var bogota = tools("CO", "Bogota", "0", "0");
        assertThat(bogota.toolsCost()).isEqualByComparingTo("2.50");
        assertThat(bogota.toolsCost().scale()).isEqualTo(2);
    }

    @Test
    void onlyTheSupportedAmericanCountriesAreServed() {
        for (String code : new String[] { "CO", "US", "MX", "CR", "EC", "PA", "PE", "BR", "AR", "BO" }) {
            assertThat(tools(code, "Bogota", "1", "0.01").total().signum()).isPositive();
        }
        assertThatThrownBy(() -> tools("FR", null, "1", "0.01"))
                .isInstanceOf(InvalidOrderException.class).hasMessageContaining("FR");
        assertThatThrownBy(() -> tools(null, null, "1", "0.01")).isInstanceOf(InvalidOrderException.class);
        // nothing to ship: no country needed, nothing charged
        assertThat(calculator.quote("FR", null, null, false, null, null, false, null, null).total())
                .isEqualByComparingTo("0");
    }

    @Test
    void colombianDestinationsMatchByCityThenProvinceThenFallBack() {
        assertThat(calculator.distanceKm("CO", "  CALI ", null).doubleValue()).isCloseTo(300, within(30.0));
        assertThat(calculator.distanceKm("CO", "Barrio X", "Cartagena").doubleValue()).isCloseTo(650, within(50.0));
        assertThat(calculator.distanceKm("CO", "Nowhere", "Unknown")).isEqualByComparingTo(
                ShippingCalculator.DOMESTIC_FALLBACK_DISTANCE_KM);
        assertThat(calculator.distanceKm("CO", "san andrés", null).doubleValue()).isGreaterThan(1000);
    }

    @Test
    void haversineIsSymmetricAndZeroForTheSamePoint() {
        double there = ShippingCalculator.haversineKm(4.7110, -74.0721, 19.4326, -99.1332);
        double back = ShippingCalculator.haversineKm(19.4326, -99.1332, 4.7110, -74.0721);
        assertThat(there).isCloseTo(back, within(1e-9));
        assertThat(ShippingCalculator.haversineKm(4.7110, -74.0721, 4.7110, -74.0721)).isZero();
    }
}
