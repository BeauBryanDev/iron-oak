package com.ironoak.dto.request;

import com.ironoak.domain.enums.BookingStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Query parameters of GET /api/admin/bookings. With no status the queue shows only open
 * bookings (REQUESTED, CONFIRMED); repeat status to see others. from/to are inclusive UTC
 * dates on the scheduled visit; categoryId is the service category.
 */
public record BookingFilter(
        List<BookingStatus> status,
        Long categoryId,
        Long serviceId,
        LocalDate from,
        LocalDate to,
        String q) {
}
