package com.ironoak.agent;

import com.ironoak.agent.tools.CNCalculatorTools;
import com.ironoak.exceptions.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CNCalculatorToolsTest {

    @Test
    void returnsTheCompleteEngineeringReportFromValidatedInputs() {
        CNCalculatorTools tools = new CNCalculatorTools();

        Map<String, Object> report = tools.calculateCncParameters(
                120.0, 10.0, 0.05, 4,
                2.0, 5.0, 1800.0, 0.25,
                5.0, 0.85, 30.0, 300.0, 0.25);

        assertThat(report).containsKeys(
                "Spindle Speed (RPM)",
                "Feed Rate (mm/min)",
                "MRR (cm^3/min)",
                "Cutting Power Pc (kW)",
                "Tool Life (min)",
                "Motor Status",
                "Load Percentage (%)");
        assertThat(report.get("Spindle Speed (RPM)")).isEqualTo(3819.72);
        assertThat(report.get("Motor Status")).isEqualTo("SAFE: Operation within motor range.");
    }

    @Test
    void rejectsUnsafeOrMathematicallyInvalidInputs() {
        CNCalculatorTools tools = new CNCalculatorTools();

        assertThatThrownBy(() -> tools.calculateCncParameters(
                120.0, 0.0, 0.05, 4,
                2.0, 5.0, 1800.0, 0.25,
                5.0, 0.85, 30.0, 300.0, 0.25))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("diameter_mm must be greater than 0");

        assertThatThrownBy(() -> tools.calculateCncParameters(
                120.0, 10.0, 0.05, 4,
                2.0, 5.0, 1800.0, 0.25,
                5.0, 1.1, 30.0, 300.0, 0.25))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("machine_efficiency must be greater than 0 and no greater than 1");
    }
}
