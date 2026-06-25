package com.acme.platform.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Base of the platform's small, consistent exception hierarchy. Each subtype
 * carries an HTTP {@code status}, a stable machine {@code code}, a human
 * {@code message}, and optional structured {@code details} — mapped uniformly to
 * the {@code {code, message, error, details?}} error body by
 * {@link GlobalExceptionHandler}.
 *
 * <p>The {@code code} doubles as the legacy {@code error} field so the unchanged
 * MCP server, conversation backend, and dashboard keep working against the exact
 * same status codes and machine values (404 / 409 / 422) they already expect.
 */
public class PlatformException extends RuntimeException {

    private final transient HttpStatus status;
    private final String code;
    private final transient Map<String, Object> details;

    public PlatformException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
