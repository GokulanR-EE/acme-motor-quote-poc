package com.acme.platform.error;

import org.springframework.http.HttpStatus;

/**
 * 404 — the requested resource is unknown, or the caller's session does not match
 * (cross-session access). Deliberately uniform so existence is never revealed:
 * unknown id and session mismatch are indistinguishable (brief §17.6).
 */
public class NotFoundException extends PlatformException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "not_found", message, null);
    }
}
