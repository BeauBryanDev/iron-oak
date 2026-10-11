package com.ironoak.services;

import com.ironoak.domain.EndMill;
import com.ironoak.domain.enums.AuditAction;
import com.ironoak.dto.request.EndMillRequest;
import com.ironoak.dto.response.AdminEndMillResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.DuplicateResourceException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.EndMillRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Staff management of end mills, like AdminProductService: rows are
 * deactivated, never deleted
 * (order lines will reference them), and stock changes only through the atomic
 * adjustment.
 * sku and vision_label must be unique: sku is checked here only (no index by
 * design), vision_label
 * also by the database. Every change is audited.
 */
@Service
@Transactional
public class AdminEndMillService {

    private final EndMillRepository endMills;
    private final AuditService audit;

    public AdminEndMillService(EndMillRepository endMills,
            AuditService audit) {

        this.endMills = endMills;
        this.audit = audit;
    }

    /**
     * All end mills including inactive ones; every filter is optional (maxStock =
     * low-stock view).
     */
    @Transactional(readOnly = true)
    public Page<AdminEndMillResponse> list(String search,
            String fluteType,
            Boolean active,
            Integer maxStock,
            Pageable pageable) {

        Specification<EndMill> spec = Specification.allOf(
                FilterSpecs.equal("isActive", active),
                FilterSpecs.hasText(fluteType)
                        ? (root, query, cb) -> cb.equal(cb.lower(root.get("fluteType")),
                                fluteType.trim().toLowerCase())
                        : null,
                maxStock == null ? null
                        : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("stockQuantity"), maxStock),
                !FilterSpecs.hasText(search) ? null
                        : (root, query, cb) -> FilterSpecs.anyContains(cb, search,
                                root.<String>get("name"), root.<String>get("sku"),
                                root.<String>get("visionLabel")));

        return endMills.findAll(spec, pageable).map(AdminEndMillResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminEndMillResponse get(Long id) {
        return AdminEndMillResponse.from(find(id));
    }

    public AdminEndMillResponse create(EndMillRequest request) {

        String sku = request.sku().trim();
        requireUnique(sku, request.visionLabel(), null);
        checkRadius(request);
        EndMill endMill = new EndMill(sku, request.name().trim(),
                request.fluteType().trim(),
                request.cuttingDiameterMm(),
                request.radiusMm(),
                request.material().trim(),
                request.overallLengthMm(),
                request.price(),
                request.stockQuantity() == null ? 0 : request.stockQuantity());
        apply(endMill, request);
        endMill.setIsActive(request.isActive() == null || request.isActive());
        AdminEndMillResponse created = AdminEndMillResponse.from(endMills.saveAndFlush(endMill));
        audit.record(AuditAction.END_MILL_CREATE, "END_MILL",
                created.id(), null, created);

        return created;
    }

    /**
     * Full replace of the editable fields. Stock is not one of them: use the stock
     * adjustment.
     */
    public AdminEndMillResponse update(Long id, EndMillRequest request) {
        if (request.stockQuantity() != null) {
            throw new BusinessRuleException("Stock changes through PATCH /api/admin/end-mills/" + id
                    + "/stock, so an edit form cannot overwrite sales made meanwhile");
        }
        EndMill endMill = find(id);
        AdminEndMillResponse before = AdminEndMillResponse.from(endMill);
        String sku = request.sku().trim();
        requireUnique(sku, request.visionLabel(), id);
        checkRadius(request);
        endMill.setSku(sku);
        endMill.setName(request.name().trim());
        endMill.setFluteType(request.fluteType().trim());
        endMill.setCuttingDiameterMm(request.cuttingDiameterMm());
        endMill.setRadiusMm(request.radiusMm());
        endMill.setMaterial(request.material().trim());
        endMill.setOverallLengthMm(request.overallLengthMm());
        endMill.setPrice(request.price());
        apply(endMill, request);
        if (request.isActive() != null) {
            endMill.setIsActive(request.isActive());
        }
        AdminEndMillResponse after = AdminEndMillResponse.from(endMills.saveAndFlush(endMill));
        audit.record(AuditAction.END_MILL_UPDATE, "END_MILL",
                id, before, after);

        return after;
    }

    public AdminEndMillResponse setActive(Long id, boolean active) {

        EndMill endMill = find(id);
        boolean was = Boolean.TRUE.equals(endMill.getIsActive());
        endMill.setIsActive(active);
        endMills.saveAndFlush(endMill);
        audit.record(AuditAction.END_MILL_ACTIVE, "END_MILL", id,
                Map.of("isActive", was),
                Map.of("isActive", active));

        return AdminEndMillResponse.from(endMill);
    }

    /** Atomic, so it cannot lose a concurrent sale; refuses to go below zero. */
    public AdminEndMillResponse adjustStock(Long id, int delta) {

        int before = find(id).getStockQuantity();
        if (delta != 0 && endMills.adjustStock(id, delta) == 0) {

            throw new BusinessRuleException("Stock cannot go below zero");
        }
        AdminEndMillResponse after = AdminEndMillResponse.from(find(id)); // adjustStock cleared the persistence context
        audit.record(AuditAction.STOCK_ADJUST, "END_MILL", id,
                Map.of("stockQuantity", before),
                Map.of("stockQuantity", after.stockQuantity(),
                        "delta", delta));

        return after;
    }

    /** The optional fields shared by create and update. */
    private static void apply(EndMill endMill, EndMillRequest request) {

        endMill.setDescription(AdminProductService.blankToNull(request.description()));
        endMill.setCutDirection(request.cutDirection() == null ? EndMill.RIGHT_HAND : request.cutDirection());
        endMill.setWeightG(request.weightG());
        endMill.setMaxRpm(request.maxRpm());
        endMill.setVisionLabel(AdminProductService.blankToNull(request.visionLabel()));
        endMill.setImageUrl(AdminProductService.blankToNull(request.imageUrl()));
    }

    private void requireUnique(String sku, String visionLabel, Long ownId) {

        endMills.findFirstBySkuIgnoreCase(sku)
                .filter(other -> !other.getId().equals(ownId))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("End mill", "sku", sku);
                });
        String label = AdminProductService.blankToNull(visionLabel);
        if (label != null) {

            endMills.findByVisionLabel(label)
                    .filter(other -> !other.getId().equals(ownId))
                    .ifPresent(other -> {
                        throw new DuplicateResourceException("End mill", "visionLabel", label);
                    });
        }
    }

    /**
     * Mirrors chk_end_mills_dimensions so staff get a readable 422 instead of a
     * generic 409.
     */
    private static void checkRadius(EndMillRequest request) {

        BigDecimal half = request.cuttingDiameterMm().divide(BigDecimal.valueOf(2));
        if (request.radiusMm().compareTo(half) > 0) {

            throw new BusinessRuleException("radiusMm must not exceed half the cutting diameter (" + half + " mm)");
        }
    }

    private EndMill find(Long id) {

        return endMills.findById(id).orElseThrow(() -> new ResourceNotFoundException("End mill", id));
    }
}
