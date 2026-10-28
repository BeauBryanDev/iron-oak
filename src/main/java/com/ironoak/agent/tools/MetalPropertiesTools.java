package com.ironoak.agent.tools;

import com.ironoak.dto.response.MaterialPropertiesResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.MetalPropertiesService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Read-only Piper lookup for the bundled CNC metal-properties reference. */
@Component
public class MetalPropertiesTools {

    private final MetalPropertiesService materials;

    public MetalPropertiesTools(MetalPropertiesService materials) {
        this.materials = materials;
    }

    @Tool(name = "get_material_properties", description = "Look up CNC milling properties of a material from Iron Oak's reference (e.g. aluminum 6061 t6, 4140, stainless steel 304): kc1 (N/mm²), mc, reference cutting speed vc_ref (m/min), Brinell hardness, density and thermal conductivity. Use only returned values; if several grades match, ask which one.")
    public List<MaterialPropertiesResponse> getMaterialProperties(
            @ToolParam(description = "Material or grade to search, such as aluminum 6061 t6, 4140, or stainless steel 304.") String material_name) {

        if (material_name == null || material_name.isBlank()) {
            throw new BusinessRuleException("material_name is required");
        }
        return materials.search(material_name);
    }
}
