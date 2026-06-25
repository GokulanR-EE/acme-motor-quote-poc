package com.acme.platform.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Pure-static id-generation tests for {@link SessionStore} — no Spring context.
 * Repository-backed access-control behaviour lives in
 * {@link SessionStoreAccessControlTest} ({@code @DataJpaTest}).
 */
class SessionStoreTest {

    @Test
    void newSessionIdIsBase64UrlNoPaddingAround43Chars() {
        String id = SessionStore.newSessionId();

        // 32 bytes base64url-no-pad -> 43 chars.
        assertThat(id).hasSize(43);
        // base64url charset only: A-Z a-z 0-9 - _ , and NO '=' padding.
        assertThat(id).matches("^[A-Za-z0-9_-]+$");
        assertThat(id).doesNotContain("=").doesNotContain("+").doesNotContain("/");
    }

    @Test
    void twoSessionIdsDiffer() {
        assertThat(SessionStore.newSessionId()).isNotEqualTo(SessionStore.newSessionId());
    }

    @Test
    void sessionIdsAreUniqueAcrossManyIterations() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(SessionStore.newSessionId());
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    void newQuoteIdIsAValidUuid() {
        String id = SessionStore.newQuoteId();

        // Parses as a UUID and re-stringifies to the same canonical form.
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }

    @Test
    void quoteIdsAreUniqueAcrossManyIterations() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(SessionStore.newQuoteId());
        }
        assertThat(ids).hasSize(10_000);
    }
}
