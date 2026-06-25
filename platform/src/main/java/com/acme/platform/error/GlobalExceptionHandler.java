package com.acme.platform.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

/**
 * Global error taxonomy (brief §2). Maps the platform's exception hierarchy and
 * Spring's request-binding failures to a single structured JSON body:
 * {@code {code, message, error, details?}}.
 *
 * <ul>
 *   <li><b>404</b> — unknown / cross-session ({@code not_found}); never reveals existence.</li>
 *   <li><b>409</b> — illegal state ({@code not_purchasable} / {@code not_issuable}).</li>
 *   <li><b>422</b> — not processable ({@code not_ready_to_price} + {@code missingFields}; semantic validation).</li>
 *   <li><b>400</b> — malformed input (missing header, unreadable / oversized body, bad param).</li>
 *   <li><b>500</b> — fallback; the cause is logged but never leaked to the client.</li>
 * </ul>
 *
 * <p>The {@code code} is mirrored into a legacy {@code error} field, and any
 * {@code details} are also flattened to the top level (e.g. {@code missingFields}),
 * so the unchanged MCP / backend / dashboard keep working against the exact shapes
 * they already consume.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<Map<String, Object>> handlePlatform(PlatformException ex) {
        return body(ex.status(), ex.code(), ex.getMessage(), ex.details());
    }

    /** A required header (e.g. {@code X-Session-Id}) was absent → 400 malformed input. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        return body(HttpStatus.BAD_REQUEST, "bad_request",
            "Missing required header: " + ex.getHeaderName(), null);
    }

    /** Unparseable / oversized request body → 400 malformed input. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "bad_request", "Malformed request body", null);
    }

    /** A path/query param failed type coercion → 400 malformed input. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "bad_request", "Invalid value for '" + ex.getName() + "'", null);
    }

    /** Bean-validation failures on a request body → 422 (semantic validation). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(fe -> details.put(fe.getField(), fe.getDefaultMessage()));
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "Request validation failed", details);
    }

    /** Bean-validation failures on method params (path/query/header) → 422. */
    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, Object>> handleParamValidation(Exception ex) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "Request validation failed", null);
    }

    /** Fallback: log the cause server-side, never leak a stack trace to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred", null);
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message,
                                                             Map<String, Object> details) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        // Legacy field consumed by the MCP / backend / dashboard — kept identical.
        out.put("error", code);
        out.put("message", message);
        if (details != null && !details.isEmpty()) {
            out.put("details", details);
            // Also flatten well-known keys to the top level for backward compatibility.
            details.forEach(out::putIfAbsent);
        }
        return ResponseEntity.status(status).body(out);
    }
}
