package com.ironoak.agent.tools;

import com.ironoak.dto.response.PowerToolInfo;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.PowerToolInfoService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Read-only Piper lookup for detailed Iron Oak power-tool information. */
@Component
public class PowerToolInfoTool {

    private final PowerToolInfoService powerTools;

    public PowerToolInfoTool(PowerToolInfoService powerTools) {
        this.powerTools = powerTools;
    }

    @Tool(name = "get_power_tool_info", description = "Search the detailed Iron Oak power-tool reference for electrical properties and technical descriptions. Search by product name or normalized key, such as electric drill, angle grinder, generator, heat gun, jigsaw, or voltage tester. Return only values from the reference: voltage, amperage, power in watts, and the deep technical description. Do not invent specifications, safety ratings, availability, price, or performance details. If a broad search returns multiple products, present the matches and ask the customer to select the exact tool.")
    public List<PowerToolInfo> getPowerToolInfo(
            @ToolParam(description = "Power-tool name or search text, such as electric drill, angle grinder, or generator.") String product_name) {

        if (product_name == null || product_name.isBlank()) {
            throw new BusinessRuleException("product_name is required");
        }
        return powerTools.search(product_name);
    }
}
