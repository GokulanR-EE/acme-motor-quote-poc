package com.acme.platform.purchase;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.acme.platform.persistence.PurchaseTokenEntity;
import com.acme.platform.persistence.PurchaseTokenRepository;

/**
 * Mints and resolves <b>signed, GUID-addressed purchase links</b> (brief §9,
 * Slice 7), now persisted via the {@link PurchaseTokenRepository} so a minted
 * link survives restart (H2 in dev, Postgres in prod).
 *
 * <p>A purchase token is a <b>high-entropy capability</b> — 32 random bytes from
 * {@link SecureRandom}, url-safe base64 — minted <b>separately from the
 * sessionId</b>. It is the sole capability for the strict landing page: whoever
 * holds the token in the URL can render that quote, and nothing else. The token
 * maps {@code token → quoteId} only; resolving it never touches the session.
 */
@Component
public class PurchaseLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final PurchaseTokenRepository repository;

    public PurchaseLinkService(PurchaseTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Mint a fresh high-entropy purchase token for {@code quoteId} and store the
     * {@code token → quoteId} mapping. The token is the landing-page capability;
     * it is not derived from, and carries no trace of, the sessionId.
     */
    public String mintToken(String quoteId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = URL_ENCODER.encodeToString(bytes);
        repository.save(new PurchaseTokenEntity(token, quoteId));
        return token;
    }

    /** Resolve a purchase token to its quoteId, or {@code null} if unknown. */
    public String resolve(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return repository.findById(token).map(PurchaseTokenEntity::getQuoteId).orElse(null);
    }
}
