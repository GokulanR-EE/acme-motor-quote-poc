package com.acme.platform.quote;

import java.util.LinkedHashMap;
import java.util.Map;

import com.acme.platform.persistence.QuoteEntity;

/**
 * A view over a persisted quote: its id, the bound strong-entropy session id, and
 * the whole-model data as a nested {@code Map} (so partial / greedy patches
 * deep-merge cleanly — mirrors the Python dict).
 *
 * <p>Always backed by a {@link QuoteEntity}. Mutating {@link #data()} mutates the
 * entity's data map; callers persist the change via {@code SessionStore.save(...)}.
 */
public final class QuoteRecord {

    private final QuoteEntity entity;

    public QuoteRecord(QuoteEntity entity) {
        this.entity = entity;
    }

    /** Convenience for tests / seeding: build a record around a fresh entity. */
    public QuoteRecord(String quoteId, String sessionId, Map<String, Object> data) {
        this(new QuoteEntity(quoteId, sessionId, data == null ? new LinkedHashMap<>() : data));
    }

    public String quoteId() {
        return entity.getQuoteId();
    }

    public String sessionId() {
        return entity.getSessionId();
    }

    public Map<String, Object> data() {
        return entity.getData();
    }

    public QuoteEntity entity() {
        return entity;
    }
}
