package com.ironoak.mapper;

import com.ironoak.domain.ServiceBooking;
import com.ironoak.dto.response.BookingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Reads the lazy customer, service offering and order; call inside a transaction. */
@Component
public class BookingMapper {

    public BookingResponse toResponse(ServiceBooking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getServiceOffering().getCode(),
                booking.getServiceOffering().getName(),
                booking.getCustomer().getName(),
                booking.getLocationAddress(),
                booking.getCountry(),
                booking.getCity(),
                booking.getScheduledAt(),
                booking.getMachineModel(),
                booking.getNotes(),
                booking.getCancelReason(),
                booking.getOrder() == null ? null : booking.getOrder().getId(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }

    public List<BookingResponse> toResponses(List<ServiceBooking> bookings) {
        return bookings.stream().map(this::toResponse).toList();
    }
}
