package com.acme.platform.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acme.platform.events.Event;
import com.acme.platform.events.EventStore;

class QuoteServiceTest {

    private EventStore events;
    private SessionStore sessions;
    private QuoteService service;

    @BeforeEach
    void setUp() {
        events = new EventStore();
        sessions = new SessionStore();
        service = new QuoteService(sessions, events);
    }

    @Test
    void createReturnsStartedStateWithSessionAndEmitsDomainEvent() {
        Map<String, Object> created = service.createQuote();

        assertThat(created).containsKeys("quoteId", "sessionId", "journeyState", "missingFields");
        assertThat(created.get("journeyState")).isEqualTo("quote_started");
        assertThat((List<?>) created.get("missingFields")).isNotEmpty();

        List<Event> domain = events.all().stream().filter(e -> e.type().equals("QUOTE_CREATED")).toList();
        assertThat(domain).hasSize(1);
        // sessionId must never appear in any event payload.
        String sid = (String) created.get("sessionId");
        assertThat(events.all()).noneMatch(e -> e.payload().toString().contains(sid));
    }

    @Test
    void getRequiresMatchingSession() {
        Map<String, Object> created = service.createQuote();
        String qid = (String) created.get("quoteId");
        String sid = (String) created.get("sessionId");

        assertThat(service.getQuote(qid, sid)).isNotNull();
        assertThat(service.getQuote(qid, "wrong")).isNull();
        assertThat(service.getQuote(qid, "")).isNull();
        assertThat(service.getQuote(qid, null)).isNull();
        // get state never leaks sessionId.
        assertThat(service.getQuote(qid, sid)).doesNotContainKey("sessionId");
    }

    @Test
    void patchDeepMergesPreservingSiblingsAndRecomputesState() {
        Map<String, Object> created = service.createQuote();
        String qid = (String) created.get("quoteId");
        String sid = (String) created.get("sessionId");

        service.applyPatch(qid, sid, Map.of("customer", Map.of("firstName", "Sam")));
        Map<String, Object> state = service.applyPatch(qid, sid, Map.of("customer", Map.of("surname", "Sample")));

        QuoteRecord rec = sessions.get(qid, sid);
        assertThat(rec.data().get("customer")).isEqualTo(Map.of("firstName", "Sam", "surname", "Sample"));
        assertThat(state.get("journeyState")).isEqualTo("collecting");
    }

    @Test
    void patchWrongSessionIsNotFound() {
        Map<String, Object> created = service.createQuote();
        String qid = (String) created.get("quoteId");
        assertThat(service.applyPatch(qid, "wrong", Map.of("customer", Map.of("firstName", "X")))).isNull();
    }

    @Test
    void deepMergeDropsNullAndEmptyLeavesNeverBlankingSiblings() {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("vehicle", new LinkedHashMap<>(Map.of("make", "Ford", "model", "Focus")));

        Map<String, Object> patch = new LinkedHashMap<>();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("make", null);     // dropped — must not blank existing
        v.put("model", "  ");    // blank string dropped
        v.put("fuel", "Petrol"); // applied
        patch.put("vehicle", v);

        QuoteService.deepMerge(base, patch);

        assertThat(base.get("vehicle")).isEqualTo(Map.of("make", "Ford", "model", "Focus", "fuel", "Petrol"));
    }

    @Test
    void fullyPopulatedReachesReadyToPrice() {
        QuoteRecord rec = sessions.create();
        // missingFields for an empty quote = all mandatory; patch them all in.
        Map<String, Object> patch = new LinkedHashMap<>();
        for (String path : RequiredFields.MANDATORY_FIELDS) {
            put(patch, path, "X");
        }
        Map<String, Object> state = service.applyPatch(rec.quoteId(), rec.sessionId(), patch);
        assertThat((List<?>) state.get("missingFields")).isEmpty();
        assertThat(state.get("journeyState")).isEqualTo("ready_to_price");
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String dotPath, Object value) {
        String[] parts = dotPath.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
        }
        cur.put(parts[parts.length - 1], value);
    }
}
