package com.spotlink.core;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final OperationalMetrics metrics;

    public GlobalExceptionHandler(OperationalMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null, request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiErrorResponse> handleValidationException(ValidationException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), ex.getFields(), request);
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    ResponseEntity<ApiErrorResponse> handleAuthentication(Exception ex, HttpServletRequest request) {
        metrics.increment("spotlink.auth.failure", "operation", authOperation(request));
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email or password", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.", null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Input validation failed", fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> fields.put(
                violation.getPropertyPath().toString(),
                violation.getMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Input validation failed", fields, request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request could not be understood.", null, request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ApiErrorResponse> handleStaleData(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "STALE_DATA", "Data changed while this request was processing. Refresh and try again.", null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        if (message != null && message.toLowerCase().contains("unique")) {
            return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "A record with these details already exists.", null, request);
        }
        log.error("Data integrity violation", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", "A database error occurred.", null, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request failure", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The service is temporarily unavailable. Try again later.", null, request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            Map<String, ?> details,
            HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                code,
                message,
                requestId,
                details,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    private String authOperation(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.endsWith("/auth/login") || path.endsWith("/v1/auth/login")) {
            return "login";
        }
        if (path.endsWith("/auth/token") || path.endsWith("/v1/auth/token")) {
            return "mobile_token";
        }
        if (path.contains("/auth/token/refresh")) {
            return "mobile_token_refresh";
        }
        return "other";
    }
}
