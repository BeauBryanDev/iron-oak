package com.ironoak.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.agent.tools.MetalPropertiesTools;
import com.ironoak.services.MetalPropertiesService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetalPropertiesToolsTest {

    @Test
    void normalizesMaterialNamesAndReturnsTheSixJsonProperties() {
        MetalPropertiesTools tools = new MetalPropertiesTools(new MetalPropertiesService(new ObjectMapper()));

        var result = tools.getMaterialProperties(" Aluminum 6061 T6 ");

        assertThat(result).singleElement().satisfies(material -> {
            assertThat(material.materialName()).isEqualTo("aluminum_6061_t6");
            assertThat(material.kc1()).isEqualTo(700.0);
            assertThat(material.mc()).isEqualTo(0.17);
            assertThat(material.vcRef()).isEqualTo(200.0);
            assertThat(material.hardness()).isEqualTo(95);
            assertThat(material.density()).isEqualTo(2.70);
            assertThat(material.thermalConductivity()).isEqualTo(167.0);
        });
    }

    @Test
    void returnsMultipleGradesForBroadSearchWithoutInventingValues() {
        MetalPropertiesTools tools = new MetalPropertiesTools(new MetalPropertiesService(new ObjectMapper()));

        var result = tools.getMaterialProperties("steel");

        assertThat(result).extracting(Material -> Material.materialName())
                .containsExactly("alloy_steel_4140", "carbon_steel_1045", "stainless_steel_304", "stainless_steel_316", "tool_steel_d2");
    }
}
