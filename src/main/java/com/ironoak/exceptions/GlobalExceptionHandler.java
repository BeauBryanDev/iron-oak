package com.ironoak.exceptions;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Every error the API returns is an RFC 7807 problem detail. Extending ResponseEntityExceptionHandler
 * also covers Spring MVC's own errors (missing ?email=, a wrong type in a path or query parameter,
 * 405, 415, an unknown URL), which otherwise come back as bare bodies with no message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(OutOfStockException.class)
    public ProblemDetail outOfStock(OutOfStockException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail duplicate(DuplicateResourceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResourceInUseException.class)
    public ProblemDetail inUse(ResourceInUseException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> tooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ProblemDetail paymentProvider(PaymentProviderException ex) {
        return ProblemDetail.forStatusAndDetail(
                ex.isNotConfigured() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                ex.isNotConfigured() ? ex.getMessage() : "The payment provider could not complete the request");
    }

    @ExceptionHandler(AssistantUnavailableException.class)
    public ProblemDetail assistantUnavailable(AssistantUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(WebhookSignatureException.class)
    public ProblemDetail badWebhookSignature(WebhookSignatureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ProblemDetail invalidOrder(InvalidOrderException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail businessRule(BusinessRuleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    // Unique-index races, e.g. two first-time checkouts with the same email at once.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail dataConflict(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with existing data; please retry");
    }

    @ExceptionHandler(InvalidImageException.class)
    public ProblemDetail invalidImage(InvalidImageException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Validation on @Validated services (e.g. MachineHealthService) rather than request bodies. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    return path.substring(path.lastIndexOf('.') + 1) + " " + v.getMessage();
                })
                .sorted()
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /** Bean validation on a body, or a filter/query parameter that cannot be converted. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    /** Unparseable JSON, or a value of the wrong type or an unknown enum constant in it. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = "Malformed JSON request body";
        if (ex.getCause() instanceof InvalidFormatException invalid && !invalid.getPath().isEmpty()) {
            detail = "Invalid value '" + invalid.getValue() + "' for field '" + fieldPath(invalid) + "'";
        } else if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()
                && !hasCause(mapping, JsonParseException.class)) { // broken syntax inside a field stays "malformed"
            detail = "Invalid value for field '" + fieldPath(mapping) + "'";
        }
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Image is too large"));
    }

    private static String describe(FieldError error) {
        return error.isBindingFailure()
                ? error.getField() + " has an invalid value '" + error.getRejectedValue() + "'"
                : error.getField() + " " + error.getDefaultMessage();
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String fieldPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining(".")).replace(".[", "[");
    }
}
