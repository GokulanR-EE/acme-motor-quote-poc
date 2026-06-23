package com.acme.platform.quote;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A stored quote: its id, the bound strong-entropy session id, and the
 * whole-model data as a nested {@code Map} (so partial / greedy patches
 * deep-merge cleanly — mirrors the Python dict).
 */
public final class QuoteRecord {

    private final String quoteId;
    private final String sessionId;
    private final Map<String, Object> data;

    public QuoteRecord(String quoteId, String sessionId, Map<String, Object> data) {
        this.quoteId = quoteId;
        this.sessionId = sessionId;
        this.data = data == null ? new LinkedHashMap<>() : data;
    }

    public String quoteId() {
        return quoteId;
    }

    public String sessionId() {
        return sessionId;
    }

    public Map<String, Object> data() {
        return data;
    }
}
