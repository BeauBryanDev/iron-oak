package com.ironoak.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.agent.tools.PowerToolInfoTool;
import com.ironoak.services.PowerToolInfoService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PowerToolInfoToolTest {

    @Test
    void returnsElectricalPropertiesAndDeepDescriptionFromTheResource() {
        PowerToolInfoTool tools = new PowerToolInfoTool(new PowerToolInfoService(new ObjectMapper()));

        var result = tools.getPowerToolInfo(" Electric Drill ");

        assertThat(result).singleElement().satisfies(tool -> {
            assertThat(tool.productName()).isEqualTo("electric_drill_ai");
            assertThat(tool.voltage()).isEqualTo(20.0);
            assertThat(tool.ampere()).isEqualTo(25.0);
            assertThat(tool.powerWatts()).isEqualTo(500.0);
            assertThat(tool.technicalDescription()).contains("brushless drill");
        });
    }

    @Test
    void returnsAllMatchesForBroadSearch() {
        PowerToolInfoTool tools = new PowerToolInfoTool(new PowerToolInfoService(new ObjectMapper()));

        var result = tools.getPowerToolInfo("digital");

        assertThat(result).extracting(tool -> tool.productName())
                .containsExactly("digital_caliper", "digital_torque_wrench", "infrared_digital_thermometer");
    }
}
