package com.ironoak.controller;

import com.ironoak.dto.request.BookingFilter;
import com.ironoak.domain.enums.BookingStatus;
import com.ironoak.dto.request.CancelBookingRequest;
import com.ironoak.dto.request.CreateBookingRequest;
import com.ironoak.dto.request.RescheduleBookingRequest;
import com.ironoak.dto.request.UpdateBookingStatusRequest;
import com.ironoak.dto.response.BookingResponse;
import com.ironoak.services.BookingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customers book, view, reschedule and cancel under /api/bookings, proving ownership with
 * the email on the booking (?email=). Staff manage the queue under /api/admin/bookings.
 */
@RestController
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/api/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request) {
        return bookings.create(request);
    }

    @GetMapping("/api/bookings")
    public List<BookingResponse> listMine(@RequestParam String email) {
        return bookings.listMine(email);
    }

    @GetMapping("/api/bookings/{id}")
    public BookingResponse get(@PathVariable Long id, @RequestParam String email) {
        return bookings.get(id, email);
    }

    @PostMapping("/api/bookings/{id}/reschedule")
    public BookingResponse reschedule(@PathVariable Long id, @RequestParam String email,
                                      @Valid @RequestBody RescheduleBookingRequest request) {
        return bookings.reschedule(id, email, request);
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, @RequestParam String email,
                                  @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return bookings.cancel(id, email, request);
    }

    @GetMapping("/api/admin/bookings")
    public PagedModel<BookingResponse> queue(
            BookingFilter filter,
            @PageableDefault(size = 20, sort = "scheduledAt") Pageable pageable) {
        return new PagedModel<>(bookings.queue(filter, pageable));
    }

    @GetMapping("/api/admin/bookings/{id}")
    public BookingResponse getForStaff(@PathVariable Long id) {
        return bookings.getForStaff(id);
    }

    @PatchMapping("/api/admin/bookings/{id}/status")
    public BookingResponse updateStatus(@PathVariable Long id,
                                        @Valid @RequestBody UpdateBookingStatusRequest request) {
        return bookings.updateStatus(id, request.status());
    }
}
