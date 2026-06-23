package com.acme.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EventStoreTest {

    @Test
    void appendAssignsMonotonicSeqAndUuidAndCategory() {
        EventStore store = new EventStore();
        Event a = store.append("QUOTE_CREATED", Map.of("quoteId", "q1"), "domain");
        Event b = store.append("API_CALL", Map.of("api", "ping"), "api");

        assertThat(a.seq()).isEqualTo(1);
        assertThat(b.seq()).isEqualTo(2);
        assertThat(a.id()).isNotBlank().isNotEqualTo(b.id());
        assertThat(a.category()).isEqualTo("domain");
        assertThat(b.category()).isEqualTo("api");
        assertThat(a.ts()).isNotBlank();
    }

    @Test
    void allPreservesAppendOrder() {
        EventStore store = new EventStore();
        store.append("A", Map.of(), "domain");
        store.append("B", Map.of(), "domain");
        store.append("C", Map.of(), "domain");

        assertThat(store.all()).extracting(Event::type).containsExactly("A", "B", "C");
    }

    @Test
    void subscribeReplaysHistoryThenStreamsLive() {
        EventStore store = new EventStore();
        store.append("HIST", Map.of(), "domain");

        List<Event> received = new ArrayList<>();
        store.subscribeWithReplay(received::add);

        assertThat(received).extracting(Event::type).containsExactly("HIST");

        store.append("LIVE", Map.of(), "domain");
        assertThat(received).extracting(Event::type).containsExactly("HIST", "LIVE");
    }

    @Test
    void unsubscribeStopsDelivery() {
        EventStore store = new EventStore();
        List<Event> received = new ArrayList<>();
        java.util.function.Consumer<Event> sub = received::add;
        store.subscribeWithReplay(sub);
        store.unsubscribe(sub);

        store.append("AFTER", Map.of(), "domain");
        assertThat(received).isEmpty();
    }
}
