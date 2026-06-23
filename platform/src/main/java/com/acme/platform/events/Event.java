package com.acme.platform.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single, immutable record of something that happened — the spine of the
 * platform's three-layer discipline. Mirrors the Python {@code Event} shape:
 * {@code {id, seq, type, category, payload, ts}}.
 */
public record Event(
    String id,
    long seq,
    String type,
    String category,
    Map<String, Object> payload,
    String ts
) {
    public static Event of(long seq, String type, String category, Map<String, Object> payload) {
        return new Event(
            UUID.randomUUID().toString(),
            seq,
            type,
            category,
            payload == null ? Map.of() : payload,
            Instant.now().toString()
        );
    }

    /** Stable, ordered JSON-friendly view matching the Python {@code to_dict()}. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("seq", seq);
        m.put("type", type);
        m.put("category", category);
        m.put("payload", payload);
        m.put("ts", ts);
        return m;
    }
}
