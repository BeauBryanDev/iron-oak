package com.ironoak.controller;

import com.ironoak.dto.response.EndMillResponse;
import com.ironoak.services.EndMillService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public end-mill catalog (milling cutters), read-only. Staff manage them under
 * /api/admin.
 */
@RestController
@RequestMapping("/api/end-mills")
public class EndMillController {

    private final EndMillService endMills;

    public EndMillController(EndMillService endMills) {
        this.endMills = endMills;
    }

    @GetMapping
    public PagedModel<EndMillResponse> list(
            @RequestParam(required = false) String fluteType,
            @RequestParam(required = false) BigDecimal diameterMm,
            @RequestParam(name = "q", required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {

        return new PagedModel<>(endMills.list(fluteType, diameterMm, search, pageable));
    }

    @GetMapping("/{id:\\d+}")
    public EndMillResponse getById(@PathVariable Long id) {

        return endMills.getById(id);
    }

    @GetMapping("/sku/{sku}")
    public EndMillResponse getBySku(@PathVariable String sku) {

        return endMills.getBySku(sku);
    }

    @GetMapping("/flute-types")
    public List<String> fluteTypes() {

        return endMills.fluteTypes();
    }

    /** The end mill for a YOLOv11 class label, e.g. ball_nose_end_mill_2mm. */
    @GetMapping("/by-vision/{visionLabel}")
    public EndMillResponse byVisionLabel(@PathVariable String visionLabel) {

        return endMills.byVisionLabel(visionLabel);
    }
}
