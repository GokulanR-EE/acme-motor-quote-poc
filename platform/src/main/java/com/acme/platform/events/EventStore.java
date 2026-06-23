package com.acme.platform.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

/**
 * In-memory, append-only event log with live fan-out — the spine of the
 * platform's three-layer discipline. Every state change ends with an event
 * appended here, which is then broadcast to all live subscribers (SSE / WS).
 *
 * <p>Thread-safe: appends are synchronized so {@code seq} is monotonic and the
 * replay-then-subscribe handoff is race-free.
 */
@Component
public class EventStore {

    private final List<Event> events = new ArrayList<>();
    private final List<Consumer<Event>> subscribers = new CopyOnWriteArrayList<>();
    private long seq = 0;

    /** Append an event and broadcast it to all live subscribers. */
    public synchronized Event append(String type, Map<String, Object> payload, String category) {
        seq += 1;
        Event event = Event.of(seq, type, category, payload);
        events.add(event);
        for (Consumer<Event> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException ignored) {
                // A failing subscriber must never break the append path.
            }
        }
        return event;
    }

    /** Snapshot of all events appended so far, in order. */
    public synchronized List<Event> all() {
        return List.copyOf(events);
    }

    /**
     * Atomically replay history to {@code onEvent} then register it for live
     * events — so a freshly connected subscriber misses nothing and sees no
     * duplicate across the handoff.
     */
    public synchronized void subscribeWithReplay(Consumer<Event> onEvent) {
        for (Event e : events) {
            onEvent.accept(e);
        }
        subscribers.add(onEvent);
    }

    public void unsubscribe(Consumer<Event> onEvent) {
        subscribers.remove(onEvent);
    }

    /** Test helper: clear all state. */
    public synchronized void reset() {
        events.clear();
        subscribers.clear();
        seq = 0;
    }
}
