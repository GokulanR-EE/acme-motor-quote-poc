package com.acme.platform.error;

import org.springframework.http.HttpStatus;

/**
 * 409 — the quote is in a state that forbids the requested transition
 * (e.g. {@code not_purchasable} / {@code not_issuable}: the quote is not cleanly
 * priced, so it cannot be purchased or issued).
 */
public class IllegalStateException409 extends PlatformException {

    public IllegalStateException409(String code, String message) {
        super(HttpStatus.CONFLICT, code, message, null);
    }
}
