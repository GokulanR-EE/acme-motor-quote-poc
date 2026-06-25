package com.acme.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.acme.platform.persistence.DomainEventRepository;
import com.acme.platform.persistence.JsonMapConverter;

/**
 * Repository-backed EventStore tests. {@code @DataJpaTest} wires a real
 * {@link DomainEventRepository} against in-memory H2; the store is constructed
 * around it so persistence (durable seq, ordered replay) and live fan-out are
 * exercised together.
 */
@DataJpaTest
@Import(JsonMapConverter.class)
class EventStoreTest {

    @Autowired DomainEventRepository repository;
    private EventStore store;

    @BeforeEach
    void setUp() {
        store = new EventStore(repository);
        store.reset();
    }

    @Test
    void appendAssignsMonotonicSeqAndUuidAndCategory() {
        Event a = store.append("QUOTE_CREATED", Map.of("quoteId", "q1"), "domain");
        Event b = store.append("API_CALL", Map.of("api", "ping"), "api");

        assertThat(a.seq()).isLessThan(b.seq());
        assertThat(a.id()).isNotBlank().isNotEqualTo(b.id());
        assertThat(a.category()).isEqualTo("domain");
        assertThat(b.category()).isEqualTo("api");
        assertThat(a.ts()).isNotBlank();
    }

    @Test
    void allPreservesAppendOrder() {
        store.append("A", Map.of(), "domain");
        store.append("B", Map.of(), "domain");
        store.append("C", Map.of(), "domain");

        assertThat(store.all()).extracting(Event::type).containsExactly("A", "B", "C");
    }

    @Test
    void subscribeReplaysHistoryThenStreamsLive() {
        store.append("HIST", Map.of(), "domain");

        List<Event> received = new ArrayList<>();
        store.subscribeWithReplay(received::add);

        assertThat(received).extracting(Event::type).containsExactly("HIST");

        store.append("LIVE", Map.of(), "domain");
        assertThat(received).extracting(Event::type).containsExactly("HIST", "LIVE");
    }

    @Test
    void unsubscribeStopsDelivery() {
        List<Event> received = new ArrayList<>();
        java.util.function.Consumer<Event> sub = received::add;
        store.subscribeWithReplay(sub);
        store.unsubscribe(sub);

        store.append("AFTER", Map.of(), "domain");
        assertThat(received).isEmpty();
    }

    @Test
    void payloadJsonSurvivesRoundTripThroughTheRepository() {
        store.append("QUOTE_PRICED", Map.of("quoteId", "q9", "outcome", "quote"), "domain");

        // Re-read through a fresh store over the same repository (simulates restart).
        EventStore reopened = new EventStore(repository);
        Event persisted = reopened.all().stream()
            .filter(e -> e.type().equals("QUOTE_PRICED")).findFirst().orElseThrow();
        assertThat(persisted.payload()).containsEntry("quoteId", "q9").containsEntry("outcome", "quote");
    }
}
