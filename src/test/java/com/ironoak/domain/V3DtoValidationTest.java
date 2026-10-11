package com.ironoak.domain;

import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.dto.request.CancelBookingRequest;
import com.ironoak.dto.request.CreateBookingRequest;
import com.ironoak.dto.request.CreatePaymentRequest;
import com.ironoak.dto.request.CreateSupportTicketRequest;
import com.ironoak.dto.request.CreateWarrantyClaimRequest;
import com.ironoak.dto.request.RescheduleBookingRequest;
import com.ironoak.dto.request.UpdateWarrantyClaimStatusRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test: bean validation on the request DTOs, no Spring context or database. */
class V3DtoValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static CreateBookingRequest booking(String email, OffsetDateTime when, String address) {
        return new CreateBookingRequest("Jane Doe", email, null, 1L, address, "CO", "Bogota", when, "VMC-650", null);
    }

    @Test
    void bookingNeedsContactAddressAndAFutureTime() {
        OffsetDateTime future = OffsetDateTime.now().plusDays(2);
        assertThat(VALIDATOR.validate(booking("jane@x.com", future, "Plant 1"))).isEmpty();

        assertThat(VALIDATOR.validate(booking("not-an-email", future, "Plant 1"))).hasSize(1);
        assertThat(VALIDATOR.validate(booking("jane@x.com", OffsetDateTime.now().minusDays(1), "Plant 1"))).hasSize(1);
        assertThat(VALIDATOR.validate(booking("jane@x.com", future, " "))).hasSize(1);
        assertThat(VALIDATOR.validate(booking("jane@x.com", null, "Plant 1"))).hasSize(1);

        assertThat(VALIDATOR.validate(new RescheduleBookingRequest(OffsetDateTime.now().minusHours(1)))).hasSize(1);
        assertThat(VALIDATOR.validate(new CancelBookingRequest(null))).isEmpty();
    }

    @Test
    void claimNeedsEmailOrderItemAndDescription() {
        assertThat(VALIDATOR.validate(new CreateWarrantyClaimRequest("jane@x.com", 1L, 2L, "Motor died"))).isEmpty();
        assertThat(VALIDATOR.validate(new CreateWarrantyClaimRequest("jane@x.com", null, null, ""))).hasSize(3);
        assertThat(VALIDATOR.validate(new UpdateWarrantyClaimStatusRequest(null, null))).hasSize(1);
        assertThat(VALIDATOR.validate(new UpdateWarrantyClaimStatusRequest(ClaimStatus.APPROVED, "Replaced"))).isEmpty();
    }

    @Test
    void paymentCarriesNoAmountAndTicketNeedsReasonAndSummary() {
        assertThat(VALIDATOR.validate(new CreatePaymentRequest(1L, "stripe", "pi_1"))).isEmpty();
        assertThat(VALIDATOR.validate(new CreatePaymentRequest(null, "", null))).hasSize(2);

        assertThat(VALIDATOR.validate(new CreateSupportTicketRequest(null, null, "Billing", "Charged twice"))).isEmpty();
        assertThat(VALIDATOR.validate(new CreateSupportTicketRequest("bad", null, "", ""))).hasSize(3);
    }
}
