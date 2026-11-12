package com.polygis.web;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.polygis.common.ApiException;
import com.polygis.common.Problems;
import com.polygis.tenancy.TenantNotResolvedException;

import lombok.extern.slf4j.Slf4j;

/**
 * RFC 7807 problem+json for every API error, each with a stable {@code code} and the {@code requestId}.
 * Unexpected exceptions become a generic 500 - their message is logged (and sent to Sentry), never returned.
 */
@Slf4j
@RestControllerAdvice // no basePackages: errors raised before a handler is chosen (405, 415) must be covered too
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return Problems.of(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return Problems.of(HttpStatus.FORBIDDEN, "forbidden", "Access denied");
    }

    @ExceptionHandler(TenantNotResolvedException.class)
    ProblemDetail tenant(TenantNotResolvedException e) {
        return Problems.of(HttpStatus.NOT_FOUND, "tenant_not_found", e.getMessage());
    }

    /** Unique constraints and foreign keys: the request conflicts with existing data. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        log.info("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return Problems.of(HttpStatus.CONFLICT, "conflict", "The request conflicts with existing data");
    }

    /** Safety net for list endpoints without a Sorts.allow(...) allowlist. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail badSort(PropertyReferenceException e) {
        return Problems.of(HttpStatus.BAD_REQUEST, "invalid_sort", "Cannot sort by '" + e.getPropertyName() + "'");
    }

    @ExceptionHandler(org.springframework.dao.InvalidDataAccessApiUsageException.class)
    ProblemDetail badQuery(org.springframework.dao.InvalidDataAccessApiUsageException e) {
        if (e.getCause() instanceof org.hibernate.query.sqm.UnknownPathException) {
            return Problems.of(HttpStatus.BAD_REQUEST, "invalid_sort", "Unknown sort or filter field");
        }
        return unexpected(e);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        return Problems.of(HttpStatus.BAD_REQUEST, "invalid_parameter", "Invalid value for '" + e.getName() + "'");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Something went wrong. Quote the request id when contacting support.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = Problems.of(HttpStatus.BAD_REQUEST, "validation", "Validation failed");
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(),
                        f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(), (a, b) -> a));
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(Problems.of(HttpStatus.BAD_REQUEST, "malformed_body", "The request body could not be read"));
    }

    /** Every other framework error (405, 415, missing parameter...) still gets a code and the request id. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        if (body == null && e instanceof org.springframework.web.ErrorResponse errorResponse) {
            body = errorResponse.getBody();
        }
        if (body instanceof ProblemDetail pd) {
            Problems.decorate(pd, HttpStatus.valueOf(status.value()).name().toLowerCase());
        }
        return super.handleExceptionInternal(e, body, headers, status, request);
    }
}
