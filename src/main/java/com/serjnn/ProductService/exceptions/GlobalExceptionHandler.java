package com.serjnn.ProductService.exceptions;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        log.warn("Product not found: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Product Not Found");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("productId", ex.getProductId());
        return problemDetail;
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ProblemDetail handleSubscriptionNotFound(SubscriptionNotFoundException ex) {
        log.warn("Subscription not found: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Subscription Not Found");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(DuplicateSubscriptionException.class)
    public ProblemDetail handleDuplicateSubscription(DuplicateSubscriptionException ex) {
        log.warn("Duplicate subscription attempt: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Duplicate Subscription");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/duplicate-subscription"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("productId", ex.getProductId());
        problemDetail.setProperty("clientId", ex.getClientId());
        return problemDetail;
    }

    @ExceptionHandler(InvalidDiscountException.class)
    public ProblemDetail handleInvalidDiscount(InvalidDiscountException ex) {
        log.warn("Invalid discount: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid Discount");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/invalid-discount"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation failed for request: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields");
        problemDetail.setTitle("Invalid Request Parameters");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/validation-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        Map<String, String> invalidFields = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            invalidFields.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problemDetail.setProperty("invalidParams", invalidFields);
        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/constraint-violation"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch error for parameter '{}': {}", ex.getName(), ex.getMessage());
        String detail = String.format("Parameter '%s' should be of type '%s'", ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setTitle("Parameter Type Mismatch");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/type-mismatch"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request body: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed or missing JSON request body");
        problemDetail.setTitle("Malformed JSON");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/malformed-json"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(ResponseStatusException ex) {
        log.warn("ResponseStatusException: {}", ex.getReason());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        problemDetail.setTitle("HTTP " + ex.getStatusCode().value());
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled internal server error occurred", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.products.serjnn.com/errors/internal-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
