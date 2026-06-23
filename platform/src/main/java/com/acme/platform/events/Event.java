package com.acme.platform.events;

/**
 * An immutable, append-only record of something that happened in the platform.
 *
 * @param id       unique identifier (UUID string)
 * @param seq      monotonically increasing sequence number, starting at 1
 * @param type     event type (e.g. "PING", "API_CALL")
 * @param category one of "domain" | "api" | "tool"
 * @param payload  arbitrary, JSON-serialisable payload
 * @param ts       ISO-8601 UTC timestamp
 */
public record Event(
        String id,
        long seq,
        String type,
        String category,
        Object payload,
        String ts) {
}
