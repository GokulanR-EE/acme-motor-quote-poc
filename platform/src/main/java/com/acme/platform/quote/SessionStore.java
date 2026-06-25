package com.acme.platform.quote;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.acme.platform.persistence.QuoteEntity;
import com.acme.platform.persistence.QuoteRepository;

/**
 * Session-scoped quote store, now backed by the {@link QuoteRepository}
 * (Spring Data JPA — H2 in dev, Postgres in prod). Quotes are keyed by
 * {@code quoteId} and bound to a <b>strong-entropy session id</b> (32 random
 * bytes, base64url-encoded, from {@link SecureRandom}). A quote is retrievable /
 * updatable <b>only</b> by presenting its session id; a missing/empty session,
 * an unknown id, or a mismatch all yield not-found — indistinguishable, so
 * existence is never revealed (brief §17.6).
 *
 * <p>Access control is the constant-time session compare done here in Java —
 * <b>never</b> a DB predicate — so a session id can never be leaked by timing or
 * by the query plan. {@link #lookup} resolves by id alone for the token-gated
 * landing page (the token is the capability, resolved upstream).
 */
@Component
public class SessionStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final QuoteRepository repository;

    public SessionStore(QuoteRepository repository) {
        this.repository = repository;
    }

    /** A GUID quote id (brief §9 — crypto.randomUUID style). */
    public static String newQuoteId() {
        return UUID.randomUUID().toString();
    }

    /** A strong-entropy session id (32 bytes → ~43 url-safe chars). */
    public static String newSessionId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    public QuoteRecord create() {
        return create(new LinkedHashMap<>());
    }

    public QuoteRecord create(Map<String, Object> data) {
        QuoteEntity entity = new QuoteEntity(newQuoteId(), newSessionId(), data);
        entity.setJourneyState("quote_started");
        return new QuoteRecord(repository.save(entity));
    }

    /** Insert/replace a record verbatim (used to self-seed the demo quote). */
    public QuoteRecord put(QuoteRecord record) {
        return new QuoteRecord(repository.save(record.entity()));
    }

    /** Persist a (mutated) record back to the store, refreshing its updated-at stamp. */
    public QuoteRecord save(QuoteRecord record) {
        record.entity().touch();
        return new QuoteRecord(repository.save(record.entity()));
    }

    /**
     * Return the record only if the session id matches; else {@code null}.
     * A missing/empty session id, an unknown quote id, or a mismatch all yield
     * {@code null} — indistinguishable, so existence is never revealed. The
     * session compare is constant-time and done in Java, never as a query filter.
     */
    public QuoteRecord get(String quoteId, String sessionId) {
        if (quoteId == null || sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        QuoteEntity entity = repository.findById(quoteId).orElse(null);
        if (entity == null) {
            return null;
        }
        if (!constantTimeEquals(entity.getSessionId(), sessionId)) {
            return null;
        }
        return new QuoteRecord(entity);
    }

    /**
     * Resolve a record by quote id <b>without</b> a session, for the strict GUID
     * purchase/quote landing page (brief §17.6). The capability is the
     * high-entropy purchase token in the URL (resolved upstream to this id), not
     * the session; this never reveals or compares the session id. Returns
     * {@code null} for an unknown id. Do <b>not</b> use on the session-gated
     * quote routes — those must go through {@link #get}.
     */
    public QuoteRecord lookup(String quoteId) {
        if (quoteId == null) {
            return null;
        }
        return repository.findById(quoteId).map(QuoteRecord::new).orElse(null);
    }

    public boolean exists(String quoteId) {
        return quoteId != null && repository.existsById(quoteId);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
