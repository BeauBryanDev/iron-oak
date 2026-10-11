package com.ironoak.services;

import com.ironoak.exceptions.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceAreaTest {

    @Test
    void onlyBogotaAndMedellinInColombiaAreServed() {
        assertThat(ServiceArea.covers("co", "Bogotá D.C.")).isTrue();
        assertThat(ServiceArea.covers("CO", "MEDELLIN")).isTrue();
        assertThat(ServiceArea.covers("CO", "Cali")).isFalse();
        assertThat(ServiceArea.covers("EC", "Bogota")).isFalse();
        assertThat(ServiceArea.covers(null, "Bogota")).isFalse();
        assertThat(ServiceArea.covers("CO", null)).isFalse();
        assertThatThrownBy(() -> ServiceArea.require("CO", "Barranquilla"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Bogotá and Medellín");
    }
}
