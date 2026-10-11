package com.ironoak.services;

import com.ironoak.domain.EndMill;
import com.ironoak.dto.response.EndMillResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.EndMillRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public end-mill catalog (storefront and Piper): active end mills only, like
 * ProductService.
 */
@Service
@Transactional(readOnly = true)
public class EndMillService {

    private final EndMillRepository endMills;

    public EndMillService(EndMillRepository endMills) {
        this.endMills = endMills;
    }

    /**
     * Active end mills; fluteType, cutting diameter (mm) and a name/SKU search are
     * optional.
     */
    public Page<EndMillResponse> list(String fluteType,
            BigDecimal diameterMm,
            String search,
            Pageable pageable) {

        Specification<EndMill> spec = Specification.allOf(
                FilterSpecs.equal("isActive", true),
                FilterSpecs.hasText(fluteType)
                        ? (root, query, cb) -> cb.equal(cb.lower(root.get("fluteType")),
                                fluteType.trim().toLowerCase())
                        : null,
                diameterMm == null ? null
                        : (root, query, cb) -> cb.equal(root.get("cuttingDiameterMm"), diameterMm),
                !FilterSpecs.hasText(search) ? null
                        : (root, query, cb) -> FilterSpecs.anyContains(cb, search,
                                root.<String>get("name"),
                                root.<String>get("sku")));

        return endMills.findAll(spec, pageable).map(EndMillResponse::from);
    }

    public EndMillResponse getById(Long id) {

        return endMills.findById(id)
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .map(EndMillResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("End mill", id));
    }

    public EndMillResponse getBySku(String sku) {

        return endMills.findFirstBySkuIgnoreCase(sku.trim())
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .map(EndMillResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("End mill", sku));
    }

    public List<String> fluteTypes() {

        return endMills.findActiveFluteTypes();
    }

    /** The end mill a YOLOv11 detection names (one class, one end mill). */
    public EndMillResponse byVisionLabel(String visionLabel) {

        return endMills.findByVisionLabel(visionLabel.trim())
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .map(EndMillResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("End mill", visionLabel));
    }
}
