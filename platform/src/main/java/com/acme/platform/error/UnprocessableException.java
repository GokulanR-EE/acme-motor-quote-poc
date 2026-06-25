package com.acme.platform.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 422 — the request is well-formed but cannot be processed in the quote's current
 * shape (e.g. {@code not_ready_to_price}: mandatory fields remain, surfaced as
 * {@code missingFields}; or semantic validation failures).
 */
public class UnprocessableException extends PlatformException {

    public UnprocessableException(String code, String message, Map<String, Object> details) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }
}
