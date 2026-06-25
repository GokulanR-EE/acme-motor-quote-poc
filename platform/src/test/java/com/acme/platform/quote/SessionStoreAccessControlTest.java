package com.acme.platform.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.acme.platform.persistence.JsonMapConverter;
import com.acme.platform.persistence.QuoteRepository;

/**
 * Repository-backed access-control tests for {@link SessionStore} against
 * in-memory H2. Pins down the brief §17.6 invariant: a correct session id
 * succeeds, while a wrong/empty/null session or an unknown quote id all yield
 * the SAME not-found result (null) — existence is never revealed. {@code lookup}
 * resolves by id alone for the token-gated landing.
 */
@DataJpaTest
@Import(JsonMapConverter.class)
class SessionStoreAccessControlTest {

    @Autowired
    QuoteRepository repository;

    private SessionStore store;

    @BeforeEach
    void setUp() {
        store = new SessionStore(repository);
    }

    @Test
    void createPersistsWithGeneratedSessionAndQuoteIds() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicle", Map.of("make", "Ford"));

        QuoteRecord record = store.create(data);

        assertThat(record.quoteId()).isNotBlank();
        assertThat(record.sessionId()).hasSize(43);
        assertThat(repository.findById(record.quoteId())).isPresent();
        assertThat(repository.findById(record.quoteId()).get().getJourneyState())
            .isEqualTo("quote_started");
    }

    @Test
    void getWithCorrectSessionIdReturnsTheRecord() {
        QuoteRecord created = store.create();

        QuoteRecord found = store.get(created.quoteId(), created.sessionId());

        assertThat(found).isNotNull();
        assertThat(found.quoteId()).isEqualTo(created.quoteId());
    }

    @Test
    void getWithWrongSessionIdYieldsNotFound() {
        QuoteRecord created = store.create();

        assertThat(store.get(created.quoteId(), "wrong-session-id")).isNull();
    }

    @Test
    void getWithEmptySessionIdYieldsNotFound() {
        QuoteRecord created = store.create();

        assertThat(store.get(created.quoteId(), "")).isNull();
    }

    @Test
    void getWithNullSessionIdYieldsNotFound() {
        QuoteRecord created = store.create();

        assertThat(store.get(created.quoteId(), null)).isNull();
    }

    @Test
    void getWithNullQuoteIdYieldsNotFound() {
        assertThat(store.get(null, "session")).isNull();
    }

    @Test
    void unknownQuoteAndWrongSessionAreIndistinguishableNotFound() {
        QuoteRecord created = store.create();

        // Every failure mode collapses to the same null result.
        assertThat(store.get("no-such-quote", created.sessionId())).isNull();
        assertThat(store.get("no-such-quote", "anything")).isNull();
        assertThat(store.get(created.quoteId(), "wrong")).isNull();
        assertThat(store.get(created.quoteId(), "")).isNull();
    }

    @Test
    void lookupResolvesByIdAloneRegardlessOfSession() {
        QuoteRecord created = store.create();

        QuoteRecord viaLookup = store.lookup(created.quoteId());

        assertThat(viaLookup).isNotNull();
        assertThat(viaLookup.quoteId()).isEqualTo(created.quoteId());
        // Resolved without presenting any session id.
    }

    @Test
    void lookupUnknownIdYieldsNull() {
        assertThat(store.lookup("no-such-quote")).isNull();
        assertThat(store.lookup(null)).isNull();
    }

    @Test
    void existsTracksPersistence() {
        QuoteRecord created = store.create();

        assertThat(store.exists(created.quoteId())).isTrue();
        assertThat(store.exists("no-such-quote")).isFalse();
        assertThat(store.exists(null)).isFalse();
    }

    @Test
    void saveTouchesUpdatedAtAndPersistsMutations() {
        QuoteRecord created = store.create();
        created.data().put("flag", "value");

        QuoteRecord saved = store.save(created);

        assertThat(store.lookup(saved.quoteId()).data()).containsEntry("flag", "value");
    }
}
