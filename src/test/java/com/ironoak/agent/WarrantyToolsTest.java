package com.ironoak.agent;

import com.ironoak.agent.tools.WarrantyTools;
import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.dto.response.WarrantyClaimResponse;
import com.ironoak.services.WarrantyClaimService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarrantyToolsTest {

    @Test
    void createClaimUsesOrderNumberEmailAndItemAndReturnsClaimIdAndStatus() {
        WarrantyClaimService claims = mock(WarrantyClaimService.class);
        WarrantyTools tools = new WarrantyTools(claims);
        WarrantyClaimResponse response = new WarrantyClaimResponse(
                42L, "WC-20261014-ABCD2345", ClaimStatus.OPEN, "Jane Doe", 7L, 11L, "Motor stopped", null,
                OffsetDateTime.now(), OffsetDateTime.now(), null);
        when(claims.createForCustomerOrderNumber("io-20261012-abcd2345", "jane@example.com", "P-100", "Motor stopped"))
                .thenReturn(response);

        WarrantyTools.CreatedClaim result = tools.createWarrantyClaim(
                "io-20261012-abcd2345", "jane@example.com", "P-100", "Motor stopped");

        assertThat(result.claimId()).isEqualTo(42L);
        assertThat(result.status()).isEqualTo(ClaimStatus.OPEN);
        verify(claims).createForCustomerOrderNumber(
                "io-20261012-abcd2345", "jane@example.com", "P-100", "Motor stopped");
    }

    @Test
    void getClaimStatusReturnsOnlyCustomerStatusFields() {
        WarrantyClaimService claims = mock(WarrantyClaimService.class);
        WarrantyTools tools = new WarrantyTools(claims);
        OffsetDateTime createdAt = OffsetDateTime.now();
        WarrantyClaimResponse response = new WarrantyClaimResponse(
                42L, "WC-20261014-ABCD2345", ClaimStatus.IN_REVIEW, "Jane Doe", 7L, 11L, "Motor stopped",
                "Technician review pending", createdAt, createdAt, null);
        when(claims.get(42L, "jane@example.com")).thenReturn(response);

        WarrantyTools.ClaimStatusResult result = tools.getClaimStatus(42L, "jane@example.com");

        assertThat(result.status()).isEqualTo(ClaimStatus.IN_REVIEW);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.resolutionNote()).isEqualTo("Technician review pending");
        verify(claims).get(42L, "jane@example.com");
    }
}
