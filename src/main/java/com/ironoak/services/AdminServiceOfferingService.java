package com.ironoak.services;

import com.ironoak.domain.enums.AuditAction;
import com.ironoak.domain.ServiceOffering;
import com.ironoak.domain.ServiceOfferingCategory;
import com.ironoak.domain.enums.PricingType;
import com.ironoak.dto.request.ServiceOfferingRequest;
import com.ironoak.dto.response.AdminServiceOfferingResponse;
import com.ironoak.dto.response.ServiceCategoryResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.DuplicateResourceException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.AdminCatalogMapper;
import com.ironoak.repository.ServiceOfferingCategoryRepository;
import com.ironoak.repository.ServiceOfferingRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Staff catalog management for technical services. Services are deactivated,
 * never deleted.
 */
@Service
@Transactional
public class AdminServiceOfferingService {

    private final ServiceOfferingRepository services;
    private final ServiceOfferingCategoryRepository categories;
    private final AdminCatalogMapper mapper;
    private final AuditService audit;

    public AdminServiceOfferingService(ServiceOfferingRepository services,
            ServiceOfferingCategoryRepository categories,
            AdminCatalogMapper mapper,
            AuditService audit) {
        this.services = services;
        this.categories = categories;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminServiceOfferingResponse> list(String search,
            Long categoryId,
            PricingType pricingType,
            Boolean active) {

        Specification<ServiceOffering> spec = Specification.allOf(
                FilterSpecs.equal("isActive", active),
                FilterSpecs.equal("pricingType", pricingType),
                categoryId == null ? null : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId),
                !FilterSpecs.hasText(search) ? null
                        : (root, query, cb) -> FilterSpecs.anyContains(cb, search,
                                root.<String>get("name"), root.<String>get("code")));

        return services.findAll(spec, Sort.by("category.name", "name")).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminServiceOfferingResponse get(Long id) {

        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<ServiceCategoryResponse> listCategories() {

        return categories.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    public AdminServiceOfferingResponse create(ServiceOfferingRequest request) {

        String code = request.code().trim();
        if (services.findByCode(code).isPresent()) {
            throw new DuplicateResourceException("Service", "code", code);
        }
        checkPricing(request);
        ServiceOffering service = new ServiceOffering(findCategory(request.serviceOfferingCategoryId()),
                code, request.name().trim(), AdminProductService.blankToNull(request.description()),
                request.pricingType(), null, null, null, null, null);

        applyPricing(service, request);

        service.setIsActive(request.isActive() == null || request.isActive());

        services.saveAndFlush(service);
        AdminServiceOfferingResponse created = mapper.toResponse(service);
        audit.record(AuditAction.SERVICE_CREATE, "SERVICE",
                service.getId(), null, created);

        return created;
    }

    public AdminServiceOfferingResponse update(Long id, ServiceOfferingRequest request) {

        ServiceOffering service = find(id);
        AdminServiceOfferingResponse before = mapper.toResponse(service);
        String code = request.code().trim();

        services.findByCode(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Service", "code", code);
                });
        checkPricing(request);

        if (!service.getCategory().getId().equals(request.serviceOfferingCategoryId())) {

            service.setCategory(findCategory(request.serviceOfferingCategoryId()));
        }
        service.setCode(code);
        service.setName(request.name().trim());
        service.setDescription(AdminProductService.blankToNull(request.description()));
        service.setPricingType(request.pricingType());
        applyPricing(service, request);

        if (request.isActive() != null) {

            service.setIsActive(request.isActive());
        }
        services.saveAndFlush(service);
        AdminServiceOfferingResponse after = mapper.toResponse(service);
        audit.record(AuditAction.SERVICE_UPDATE, "SERVICE",
                id, before, after);

        return after;
    }

    public AdminServiceOfferingResponse setActive(Long id, boolean active) {

        ServiceOffering service = find(id);
        AdminServiceOfferingResponse before = mapper.toResponse(service);
        service.setIsActive(active);
        services.saveAndFlush(service);
        AdminServiceOfferingResponse after = mapper.toResponse(service);
        audit.record(AuditAction.SERVICE_ACTIVE, "SERVICE",
                id, before, after);

        return after;
    }

    private ServiceOffering find(Long id) {
        return services.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service", id));
    }

    private ServiceOfferingCategory findCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service category", id));
    }

    /**
     * Mirrors the chk_pricing constraint so staff get a readable 422 instead of a
     * generic 409.
     */
    private void checkPricing(ServiceOfferingRequest r) {

        switch (r.pricingType()) {
            case FIXED -> {
                if (r.fixedPrice() == null || r.priceUnit() == null || r.priceUnit().isBlank()) {
                    throw new BusinessRuleException("FIXED services need fixedPrice and priceUnit");
                }
            }
            case HOURLY -> {
                if (r.hourlyRate() == null || r.estimatedMinHours() == null
                        || r.estimatedMaxHours() == null || r.priceUnit() == null || r.priceUnit().isBlank()) {
                    throw new BusinessRuleException(
                            "HOURLY services need hourlyRate, estimatedMinHours, estimatedMaxHours and priceUnit");
                }
                if (r.estimatedMaxHours().compareTo(r.estimatedMinHours()) < 0) {
                    throw new BusinessRuleException("estimatedMaxHours must be at least estimatedMinHours");
                }
            }
            case QUOTE -> {
                // priced per quote: no stored price at all
            }
        }
    }

    /**
     * Stores only the price fields that apply to the pricing type, so stale values
     * cannot linger.
     */
    private void applyPricing(ServiceOffering service, ServiceOfferingRequest r) {

        PricingType type = r.pricingType();
        BigDecimal fixed = type == PricingType.FIXED ? r.fixedPrice() : null;
        boolean hourly = type == PricingType.HOURLY;
        service.setFixedPrice(fixed);
        service.setHourlyRate(hourly ? r.hourlyRate() : null);
        service.setEstimatedMinHours(hourly ? r.estimatedMinHours() : null);
        service.setEstimatedMaxHours(hourly ? r.estimatedMaxHours() : null);
        service.setPriceUnit(type == PricingType.QUOTE ? null : AdminProductService.blankToNull(r.priceUnit()));
    }
}
