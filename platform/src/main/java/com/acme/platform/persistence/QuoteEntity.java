package com.acme.platform.persistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Persistent quote — the whole-model quote payload and its derived state.
 *
 * <p>The quote {@code data} is the nested whole-model map stored as a JSON column
 * (so partial / greedy patches deep-merge cleanly); {@code pricing} and
 * {@code policy} are convenience JSON projections of the priced / issued sections
 * for query-side visibility. {@code journeyState} and {@code currentOutcome} are
 * persisted too so the dashboard / restart can read them without recomputation.
 *
 * <p>Bound to a strong-entropy {@code sessionId}; access control is the
 * constant-time session compare performed by the store, never a DB predicate.
 */
@Entity
@Table(name = "quotes")
public class QuoteEntity {

    @Id
    @Column(name = "quote_id", nullable = false, updatable = false, length = 64)
    private String quoteId;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "journey_state", length = 64)
    private String journeyState;

    @Column(name = "current_outcome", length = 32)
    private String currentOutcome;

    @Lob
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "data", columnDefinition = "CLOB")
    private Map<String, Object> data = new LinkedHashMap<>();

    @Lob
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "pricing", columnDefinition = "CLOB")
    private Map<String, Object> pricing;

    @Lob
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "policy", columnDefinition = "CLOB")
    private Map<String, Object> policy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuoteEntity() {
        // JPA
    }

    public QuoteEntity(String quoteId, String sessionId, Map<String, Object> data) {
        this.quoteId = quoteId;
        this.sessionId = sessionId;
        this.data = data == null ? new LinkedHashMap<>() : data;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getJourneyState() {
        return journeyState;
    }

    public void setJourneyState(String journeyState) {
        this.journeyState = journeyState;
    }

    public String getCurrentOutcome() {
        return currentOutcome;
    }

    public void setCurrentOutcome(String currentOutcome) {
        this.currentOutcome = currentOutcome;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : data;
    }

    public Map<String, Object> getPricing() {
        return pricing;
    }

    public void setPricing(Map<String, Object> pricing) {
        this.pricing = pricing;
    }

    public Map<String, Object> getPolicy() {
        return policy;
    }

    public void setPolicy(Map<String, Object> policy) {
        this.policy = policy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
