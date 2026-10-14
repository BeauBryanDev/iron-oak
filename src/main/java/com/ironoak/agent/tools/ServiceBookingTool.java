package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.domain.enums.BookingStatus;
import com.ironoak.domain.enums.PricingType;
import com.ironoak.dto.request.CreateBookingRequest;
import com.ironoak.dto.response.BookingResponse;
import com.ironoak.dto.response.ServiceOfferingResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.BookingService;
import com.ironoak.services.CountryCodes;
import com.ironoak.services.ServiceArea;
import com.ironoak.services.ServiceCatalogService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Piper books a technician visit (BookingService.create). The booking rules stay
 * in the backend; this tool adds what the REST controller would do with @Valid
 * (a tool call skips it): bean validation, including a visit date in the future.
 *
 * A new booking is only REQUESTED: staff confirm it after checking a technician
 * is free, which creates the order the customer pays by order number.
 */
@Component
public class ServiceBookingTool {

    /** Times without an offset are Bogotá time, where the technicians work. */
    static final ZoneId SERVICE_ZONE = ZoneId.of("America/Bogota");

    private final BookingService bookings;
    private final ServiceCatalogService catalog;
    private final Validator validator;

    public ServiceBookingTool(BookingService bookings,
            ServiceCatalogService catalog,
            Validator validator) {

        this.bookings = bookings;
        this.catalog = catalog;
        this.validator = validator;
    }

    @Tool(name = "create_service_booking", description = "Book an Iron Oak technician visit, only after the "
            + "customer has confirmed your summary of every detail. Technicians work ONLY in Bogota and "
            + "Medellin, Colombia: for any other city say so and do not call this tool. Get the service code "
            + "from list_services. The visit must be in the future. The booking is created as REQUESTED, "
            + "which is NOT a confirmed visit: Iron Oak staff check that a technician is available on that "
            + "date and then confirm it; at confirmation the price is set (FIXED services at their listed "
            + "price, HOURLY services from the estimated hours, QUOTE services priced by staff) and the "
            + "customer receives an order number to pay online. Never promise the date or a final price. "
            + "Tell the customer their booking_id and to keep the email they used: with both they can check "
            + "or cancel it, and a date change before confirmation goes through Iron Oak staff. Calling again "
            + "with the same email, service and time returns the existing booking instead of a second one.")
    public BookingResult createServiceBooking(
            @ToolParam(description = "Service code from list_services, e.g. PREVENTIVE_MAINTENANCE.")
            String service_code,
            @ToolParam(description = "Customer full name.")
            String customer_name,
            @ToolParam(description = "Customer email; the booking is tied to it.")
            String customer_email,
            @ToolParam(required = false, description = "Customer phone, optional.")
            String customer_phone,
            @ToolParam(description = "Street address where the machine is, as the customer gave it.")
            String location_address,
            @ToolParam(description = "Country name or ISO code; only Colombia (CO) is served.")
            String country,
            @ToolParam(description = "City: Bogota or Medellin.")
            String city,
            @ToolParam(description = "Requested visit date and time, ISO-8601, e.g. 2026-10-20T09:00 "
                    + "(Bogota time when no offset is given) or 2026-10-20T09:00-05:00.")
            String scheduled_at,
            @ToolParam(required = false, description = "Machine make and model, optional.")
            String machine_model,
            @ToolParam(required = false, description = "What is wrong or what the customer needs, optional.")
            String notes) {

        ServiceOfferingResponse service = catalog.getService(required(service_code, "service_code")
                .toUpperCase());
        String countryCode = CountryCodes.toIsoCode(country);

        // the backend refuses this too; checking first gives the customer a clear answer
        ServiceArea.require(countryCode, city);

        CreateBookingRequest request = new CreateBookingRequest(
                trimToNull(customer_name),
                trimToNull(customer_email),
                trimToNull(customer_phone),
                service.id(),
                trimToNull(location_address),
                countryCode,
                trimToNull(city),
                parseVisitTime(scheduled_at),
                trimToNull(machine_model),
                trimToNull(notes));

        validate(request);

        Optional<BookingResponse> existing = sameOpenBooking(request, service.code());

        if (existing.isPresent()) {
            return BookingResult.from(existing.get(), service.pricingType(), true);
        }
        return BookingResult.from(bookings.create(request), service.pricingType(), false);
    }

    /** "2026-10-20T09:00" (Bogotá) or "2026-10-20T09:00-05:00". */
    static OffsetDateTime parseVisitTime(String value) {

        String text = required(value, "scheduled_at");
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(text).atZone(SERVICE_ZONE).toOffsetDateTime();
            } catch (DateTimeParseException e) {
                throw new BusinessRuleException("scheduled_at must be an ISO date and time such as "
                        + "2026-10-20T09:00, not '" + text + "'");
            }
        }
    }

    /** The checks @Valid would run at the REST controller (email format, lengths, future date). */
    private void validate(CreateBookingRequest request) {

        Set<ConstraintViolation<CreateBookingRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            throw new BusinessRuleException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
    }

    /** A retry of the same request returns the booking already made. */
    private Optional<BookingResponse> sameOpenBooking(CreateBookingRequest request, String serviceCode) {

        return bookings.listMine(request.customerEmail()).stream()
                .filter(b -> b.status() == BookingStatus.REQUESTED)
                .filter(b -> serviceCode.equals(b.serviceCode()))
                .filter(b -> b.scheduledAt().toInstant().equals(request.scheduledAt().toInstant()))
                .max(Comparator.comparing(BookingResponse::createdAt));
    }

    private static String required(String value, String field) {

        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(field + " is required");
        }
        return value.strip();
    }

    private static String trimToNull(String value) {

        return value == null || value.isBlank() ? null : value.strip();
    }

    public record BookingResult(
            @JsonProperty("booking_id") Long bookingId,
            @JsonProperty("booking_number") String bookingNumber,
            BookingStatus status,
            @JsonProperty("service_code") String serviceCode,
            @JsonProperty("service_name") String serviceName,
            @JsonProperty("pricing_type") PricingType pricingType,
            String city,
            @JsonProperty("scheduled_at") OffsetDateTime scheduledAt,
            @JsonProperty("already_existed") boolean alreadyExisted,
            @JsonProperty("next_steps") String nextSteps) {

        static BookingResult from(BookingResponse booking, PricingType pricing, boolean alreadyExisted) {

            return new BookingResult(booking.id(),
                    booking.bookingNumber(),
                    booking.status(),
                    booking.serviceCode(),
                    booking.serviceName(),
                    pricing,
                    booking.city(),
                    booking.scheduledAt(),
                    alreadyExisted,
                    "Requested, not yet confirmed. Iron Oak staff will check that a technician is "
                            + "available on this date and confirm it; the customer then receives an order "
                            + "number to pay online. Keep the booking id and the email to check or cancel it.");
        }
    }
}
