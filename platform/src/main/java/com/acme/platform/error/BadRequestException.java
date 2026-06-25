package com.acme.platform.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 400 — malformed input: a missing required header, an unparseable / oversized
 * body, or a path/query parameter that fails validation.
 */
public class BadRequestException extends PlatformException {

    public BadRequestException(String message) {
        this(message, null);
    }

    public BadRequestException(String message, Map<String, Object> details) {
        super(HttpStatus.BAD_REQUEST, "bad_request", message, details);
    }
}
