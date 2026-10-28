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

    @Tool(name = "get_power_tool_info", description = "Look up a power tool's electrical data (voltage, amperage, watts) and technical description in Iron Oak's reference, by name (e.g. electric drill, angle grinder, generator). Use only returned values; if several tools match, ask which one.")
    public List<PowerToolInfo> getPowerToolInfo(
            @ToolParam(description = "Power-tool name or search text, such as electric drill, angle grinder, or generator.") String product_name) {

        if (product_name == null || product_name.isBlank()) {
            throw new BusinessRuleException("product_name is required");
        }
        return powerTools.search(product_name);
    }
}
