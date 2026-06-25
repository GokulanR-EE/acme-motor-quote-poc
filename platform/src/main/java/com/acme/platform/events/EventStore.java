package com.acme.platform.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.acme.platform.persistence.DomainEventEntity;
import com.acme.platform.persistence.DomainEventRepository;

/**
 * Durable, append-only event log with live fan-out — the spine of the platform's
 * three-layer discipline. Every state change ends with an event appended here,
 * which is <b>persisted</b> via the {@link DomainEventRepository} (H2 in dev,
 * Postgres in prod) <b>and</b> broadcast to all live subscribers (SSE / WS).
 *
 * <p>{@code seq} is the durable, monotonic sequence assigned by the database
 * (the entity's auto-increment id), so it survives restart and a replay
 * reproduces the exact frames the dashboard saw. Appends are synchronized so the
 * persist-then-broadcast step and the replay-then-subscribe handoff are race-free.
 */
@Component
public class EventStore {

    private final DomainEventRepository repository;
    private final List<Consumer<Event>> subscribers = new CopyOnWriteArrayList<>();

    public EventStore(DomainEventRepository repository) {
        this.repository = repository;
    }

    /** Append an event: persist it (assigning the durable seq), then broadcast live. */
    public synchronized Event append(String type, Map<String, Object> payload, String category) {
        Event seed = Event.of(0, type, category, payload);
        DomainEventEntity saved = repository.save(new DomainEventEntity(
            seed.id(), seed.type(), seed.category(), seed.payload(), seed.ts()));
        Event event = new Event(saved.getEventId(), saved.getSeq(), saved.getType(),
            saved.getCategory(), saved.getPayload(), saved.getTs());
        for (Consumer<Event> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException ignored) {
                // A failing subscriber must never break the append path.
            }
        }
        return event;
    }

    /** Snapshot of all events appended so far, in append (seq) order. */
    public List<Event> all() {
        return repository.findAllByOrderBySeqAsc().stream().map(EventStore::toEvent).toList();
    }

    /**
     * Atomically replay history to {@code onEvent} then register it for live
     * events — so a freshly connected subscriber misses nothing and sees no
     * duplicate across the handoff.
     */
    public synchronized void subscribeWithReplay(Consumer<Event> onEvent) {
        for (DomainEventEntity e : repository.findAllByOrderBySeqAsc()) {
            onEvent.accept(toEvent(e));
        }
        subscribers.add(onEvent);
    }

    public void unsubscribe(Consumer<Event> onEvent) {
        subscribers.remove(onEvent);
    }

    /** Test helper: clear live subscribers and persisted events. */
    public synchronized void reset() {
        subscribers.clear();
        repository.deleteAll();
    }

    private static Event toEvent(DomainEventEntity e) {
        return new Event(e.getEventId(), e.getSeq(), e.getType(), e.getCategory(), e.getPayload(), e.getTs());
    }
}
