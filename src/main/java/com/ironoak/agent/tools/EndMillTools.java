package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.dto.response.EndMillResponse;
import com.ironoak.services.EndMillService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Read-only end-mill catalog tools exposed to Piper. */
@Component
public class EndMillTools {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final EndMillService endMills;

    public EndMillTools(EndMillService endMills) {
        this.endMills = endMills;
    }

    @Tool(name = "get_end_mills", description = "List active milling cutters from the Iron Oak end-mill catalog. Optional filters can match name or SKU, flute type, and exact cutting diameter in millimetres. Prices and availability come from the database, you also have a vision tool for end-mill detection. Each result has reference_id: use it with item_type END_MILL in create_order.")
    public List<EndMillResult> getEndMills(
            @ToolParam(required = false, description = "Optional text matching the end-mill name or SKU.") String query,
            @ToolParam(required = false, description = "Optional flute type, such as Ball Nose or Fishtail.") String flute_type,
            @ToolParam(required = false, description = "Optional exact cutting diameter in millimetres.") BigDecimal diameter_mm,
            @ToolParam(required = false, description = "Number of results, default 20, maximum 50.") Integer limit) {

        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;

        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {

            throw new IllegalArgumentException("limit must be between 1 and 50");
        }

        return endMills.list(flute_type,
                diameter_mm,
                query,
                PageRequest.of(0, effectiveLimit, Sort.by(Sort.Direction.ASC, "name")))
                .getContent().stream().map(EndMillResult::from).toList();
    }

    public record EndMillResult(
            @JsonProperty("reference_id") Long referenceId,
            String sku,
            String name,
            String description,
            @JsonProperty("flute_type") String fluteType,
            @JsonProperty("cut_direction") String cutDirection,
            @JsonProperty("cutting_diameter_mm") BigDecimal cuttingDiameterMm,
            @JsonProperty("radius_mm") BigDecimal radiusMm,
            String material,
            @JsonProperty("overall_length_mm") BigDecimal overallLengthMm,
            @JsonProperty("weight_g") BigDecimal weightG,
            BigDecimal price,
            @JsonProperty("max_rpm") Integer maxRpm,
            @JsonProperty("in_stock") boolean inStock,
            @JsonProperty("image_url") String imageUrl) {

        static EndMillResult from(EndMillResponse endMill) {

            return new EndMillResult(endMill.id(),
                    endMill.sku(),
                    endMill.name(), endMill.description(),
                    endMill.fluteType(),
                    endMill.cutDirection(),
                    endMill.cuttingDiameterMm(),
                    endMill.radiusMm(),
                    endMill.material(),
                    endMill.overallLengthMm(),
                    endMill.weightG(),
                    endMill.price(),
                    endMill.maxRpm(),
                    endMill.inStock(),
                    endMill.imageUrl());
        }
    }
}
