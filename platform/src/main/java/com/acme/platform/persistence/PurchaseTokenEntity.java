package com.acme.platform.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent purchase token → quoteId mapping. The token is a high-entropy
 * capability minted separately from the sessionId; possession of it is the sole
 * access control for the strict GUID landing page.
 */
@Entity
@Table(name = "purchase_tokens")
public class PurchaseTokenEntity {

    @Id
    @Column(name = "token", nullable = false, updatable = false, length = 128)
    private String token;

    @Column(name = "quote_id", nullable = false, length = 64)
    private String quoteId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PurchaseTokenEntity() {
        // JPA
    }

    public PurchaseTokenEntity(String token, String quoteId) {
        this.token = token;
        this.quoteId = quoteId;
        this.createdAt = Instant.now();
    }

    public String getToken() {
        return token;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
