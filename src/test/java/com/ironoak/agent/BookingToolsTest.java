package com.ironoak.agent;

import com.ironoak.agent.tools.BookingTools;
import com.ironoak.dto.request.CancelBookingRequest;
import com.ironoak.dto.response.BookingResponse;
import com.ironoak.services.BookingService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingToolsTest {

    @Test
    void getBookingDelegatesIdAndEmailOwnershipCheckToBookingService() {
        BookingService bookings = mock(BookingService.class);
        BookingTools tools = new BookingTools(bookings);
        BookingResponse response = mock(BookingResponse.class);
        when(bookings.get(17L, "jane@example.com")).thenReturn(response);

        assertThat(tools.getServiceBooking(17L, "jane@example.com")).isSameAs(response);
        verify(bookings).get(17L, "jane@example.com");
    }
    @Test
    void cancelBookingDelegatesIdEmailAndReasonToBookingService() {
        BookingService bookings = mock(BookingService.class);
        BookingTools tools = new BookingTools(bookings);
        BookingResponse response = mock(BookingResponse.class);
        when(bookings.cancel(eq(17L), eq("jane@example.com"), any(CancelBookingRequest.class))).thenReturn(response);

        assertThat(tools.cancelServiceBooking(17L, "jane@example.com", "Changed plans")).isSameAs(response);
        verify(bookings).cancel(eq(17L), eq("jane@example.com"), any(CancelBookingRequest.class));
    }
}
