package com.acme.platform.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

/**
 * Thread-safe, append-only event store and live publisher.
 *
 * <p>Appended events are kept in insertion (sequence) order and broadcast to any
 * registered listeners (the live channel registers itself here). This is the
 * "source of truth" feed that powers the dashboard.
 */
@Component
public class EventStore {

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(0);

    /**
     * Append an event with an explicit category.
     *
     * @param type     event type
     * @param payload  JSON-serialisable payload
     * @param category one of "domain" | "api" | "tool"
     * @return the stored event
     */
    public Event append(String type, Object payload, String category) {
        Event event = new Event(
                UUID.randomUUID().toString(),
                seq.incrementAndGet(),
                type,
                category,
                payload,
                Instant.now().toString());
        events.add(event);
        broadcast(event);
        return event;
    }

    /**
     * Append a "domain" event (category defaults to "domain").
     */
    public Event append(String type, Object payload) {
        return append(type, payload, "domain");
    }

    /**
     * @return all events in append (sequence) order.
     */
    public List<Event> all() {
        return List.copyOf(events);
    }

    /**
     * Register a listener that is invoked for every subsequently appended event.
     * Used by the live channel (WebSocket / SSE) to stream new events.
     */
    public void addListener(Consumer<Event> listener) {
        listeners.add(listener);
    }

    private void broadcast(Event event) {
        for (Consumer<Event> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException ex) {
                // A misbehaving subscriber must never break the append path.
            }
        }
    }
}
