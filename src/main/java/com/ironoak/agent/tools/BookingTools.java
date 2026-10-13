package com.ironoak.agent.tools;

import com.ironoak.dto.request.CancelBookingRequest;
import com.ironoak.dto.response.BookingResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.BookingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Customer-owned read access to Piper service bookings. */
@Component
public class BookingTools {

    private final BookingService bookings;

    public BookingTools(BookingService bookings) {
        this.bookings = bookings;
    }

    @Tool(name = "get_service_booking", description = "Retrieve one customer's service booking by booking id and the email used for the booking. The backend verifies ownership and returns a generic not-found result when the booking does not belong to that email.")
    public BookingResponse getServiceBooking(
            @ToolParam(description = "The booking database identifier returned when the booking was created.") Long booking_id,
            @ToolParam(description = "Email address used for the booking.") String email) {

        return bookings.get(booking_id, email);
    }

    @Tool(name = "cancel_service_booking", description = "Cancel an open customer service booking using the booking id and the email used for the booking. This is a soft cancellation; the booking record is retained. Paid bookings cannot be cancelled by Piper and must be handled by customer support.")
    public BookingResponse cancelServiceBooking(
            @ToolParam(description = "The booking identifier.") Long booking_id,
            @ToolParam(description = "Email address used for the booking.") String email,
            @ToolParam(required = false, description = "Optional cancellation reason, maximum 1000 characters.") String reason) {

        if (reason != null && reason.length() > 1000) {
            throw new BusinessRuleException("reason must not exceed 1000 characters");
        }
        return bookings.cancel(booking_id, email, reason == null ? null : new CancelBookingRequest(reason));
    }
}
