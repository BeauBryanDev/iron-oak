package com.ironoak.mapper;

import com.ironoak.domain.WarrantyClaim;
import com.ironoak.dto.response.WarrantyClaimResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Reads the lazy customer and order; call inside a transaction. */
@Component
public class WarrantyClaimMapper {

    public WarrantyClaimResponse toResponse(WarrantyClaim claim) {
        return new WarrantyClaimResponse(
                claim.getId(),
                claim.getStatus(),
                claim.getCustomer().getName(),
                claim.getOrder().getId(),
                claim.getOrderItemId(),
                claim.getDescription(),
                claim.getResolutionNote(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                claim.getResolvedAt());
    }

    public List<WarrantyClaimResponse> toResponses(List<WarrantyClaim> claims) {
        return claims.stream().map(this::toResponse).toList();
    }
}
