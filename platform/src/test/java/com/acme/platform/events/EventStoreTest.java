package com.acme.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EventStoreTest {

    @Test
    void seqIncrementsFromOne() {
        EventStore store = new EventStore();
        Event a = store.append("A", Map.of("n", 1));
        Event b = store.append("B", Map.of("n", 2));
        Event c = store.append("C", Map.of("n", 3));

        assertThat(a.seq()).isEqualTo(1);
        assertThat(b.seq()).isEqualTo(2);
        assertThat(c.seq()).isEqualTo(3);
    }

    @Test
    void idsAreUniqueUuids() {
        EventStore store = new EventStore();
        Event a = store.append("A", Map.of());
        Event b = store.append("B", Map.of());

        assertThat(a.id()).isNotBlank();
        assertThat(b.id()).isNotBlank();
        assertThat(a.id()).isNotEqualTo(b.id());
        // valid UUID format
        assertThat(a.id()).matches("[0-9a-fA-F-]{36}");
    }

    @Test
    void categoryDefaultsToDomain() {
        EventStore store = new EventStore();
        Event domain = store.append("A", Map.of());
        Event api = store.append("B", Map.of(), "api");

        assertThat(domain.category()).isEqualTo("domain");
        assertThat(api.category()).isEqualTo("api");
    }

    @Test
    void allReturnsEventsInAppendOrder() {
        EventStore store = new EventStore();
        store.append("A", Map.of());
        store.append("B", Map.of());
        store.append("C", Map.of());

        List<Event> all = store.all();
        assertThat(all).extracting(Event::type).containsExactly("A", "B", "C");
        assertThat(all).extracting(Event::seq).containsExactly(1L, 2L, 3L);
    }

    @Test
    void appendBroadcastsToRegisteredListeners() {
        EventStore store = new EventStore();
        List<Event> received = new ArrayList<>();
        store.addListener(received::add);

        Event appended = store.append("PING", Map.of("k", "v"));

        assertThat(received).containsExactly(appended);
    }
}
