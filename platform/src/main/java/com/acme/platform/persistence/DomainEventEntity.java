package com.acme.platform.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Persistent, append-only event record — the durable spine of the three-layer
 * discipline. Mirrors the wire {@code Event} shape: {@code {id, seq, type,
 * category, payload, ts}}, with the payload stored as a JSON column.
 *
 * <p>{@code seq} is the monotonic sequence assigned at append time (the entity's
 * surrogate auto-increment id). The wire {@code id} (a UUID) and {@code ts} are
 * persisted verbatim so a replay after restart reproduces the exact frames the
 * dashboard saw.
 */
@Entity
@Table(name = "domain_events")
public class DomainEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq", nullable = false, updatable = false)
    private Long seq;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "category", length = 32)
    private String category;

    @Lob
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "payload", columnDefinition = "CLOB")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(name = "ts", nullable = false, length = 64)
    private String ts;

    protected DomainEventEntity() {
        // JPA
    }

    public DomainEventEntity(String eventId, String type, String category,
                             Map<String, Object> payload, String ts) {
        this.eventId = eventId;
        this.type = type;
        this.category = category;
        this.payload = payload == null ? new LinkedHashMap<>() : payload;
        this.ts = ts;
    }

    public Long getSeq() {
        return seq;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getTs() {
        return ts;
    }
}
