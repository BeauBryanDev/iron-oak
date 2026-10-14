package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.dto.response.WarrantyClaimResponse;
import com.ironoak.services.WarrantyClaimService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Customer-owned warranty claim tools. Eligibility remains in
 * WarrantyClaimService.
 */
@Component
public class WarrantyTools {

        private final WarrantyClaimService claims;

        public WarrantyTools(WarrantyClaimService claims) {
                this.claims = claims;
        }

        @Tool(name = "create_warranty_claim", description = "Submit a warranty claim after the customer confirms. Use the customer-facing order number, the email used for the order, and the purchased product name or SKU; for a milling machine use its model code. The backend verifies ownership, completed and delivered order status, item membership, warranty eligibility, and duplicate active claims.")
        public CreatedClaim createWarrantyClaim(
                        @ToolParam(description = "Customer-facing order number, for example IO-20261012-ABCD2345.") String order_number,
                        @ToolParam(description = "Email address used when the order was created.") String email,
                        @ToolParam(description = "Purchased product name or SKU; for a milling machine, use its model code.") String item,
                        @ToolParam(description = "Clear description of the defect or warranty issue.") String description) {

                WarrantyClaimResponse claim = claims.createForCustomerOrderNumber(order_number,
                                email, item, description);

                return new CreatedClaim(claim.id(),
                                claim.claimNumber(),
                                claim.status());
        }

        @Tool(name = "get_claim_status", description = "Get a customer's warranty claim status using the claim id and the email used for the claim. Ownership is verified by the backend and unauthorized claims return the generic not-found result.")
        public ClaimStatusResult getClaimStatus(
                        @ToolParam(description = "Warranty claim identifier returned when the claim was created.") Long claim_id,
                        @ToolParam(description = "Email address used for the warranty claim.") String email) {

                WarrantyClaimResponse claim = claims.get(claim_id, email);

                return new ClaimStatusResult(claim.status(),
                                claim.createdAt(),
                                claim.resolutionNote());
        }

        public record CreatedClaim(
                        @JsonProperty("claim_id") Long claimId,
                        @JsonProperty("claim_number") String claimNumber,
                        ClaimStatus status) {
        }

        public record ClaimStatusResult(
                        ClaimStatus status,
                        @JsonProperty("created_at") OffsetDateTime createdAt,
                        @JsonProperty("resolution_note") String resolutionNote) {
        }
}
