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

    @Tool(name = "get_material_properties", description = "Look up authoritative CNC milling material properties from the bundled metal_properties.json reference. Search by material name, grade, or normalized key, for example aluminum_6061_t6, aluminum 6061 t6, 4140, or stainless steel 304. Return only values from the reference: kc1 in N/mm², mc dimensionless, reference cutting speed vc_ref in m/min, Brinell hardness HB, density in g/cm³, and thermal conductivity in W/m·K. Do not calculate, interpolate, or invent missing material values. If multiple materials match a broad search, present each match and ask the customer to select the exact grade before using it for CNC calculations.")
    public List<MaterialPropertiesResponse> getMaterialProperties(
            @ToolParam(description = "Material or grade to search, such as aluminum 6061 t6, 4140, or stainless steel 304.") String material_name) {

        if (material_name == null || material_name.isBlank()) {
            throw new BusinessRuleException("material_name is required");
        }
        return materials.search(material_name);
    }
}
