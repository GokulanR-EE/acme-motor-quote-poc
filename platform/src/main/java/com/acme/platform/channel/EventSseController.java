package com.acme.platform.channel;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.acme.platform.events.Event;
import com.acme.platform.events.EventStore;

/**
 * Live SSE channel at {@code GET /events}. On subscribe, replays every existing
 * event, then streams newly appended events. Registers itself as a listener on
 * the {@link EventStore}; appends are broadcast to all open emitters.
 */
@RestController
public class EventSseController {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final EventStore eventStore;

    public EventSseController(EventStore eventStore) {
        this.eventStore = eventStore;
        eventStore.addListener(this::broadcast);
    }

    @GetMapping("/events")
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        emitters.add(emitter);

        // Replay existing events on subscribe.
        for (Event event : eventStore.all()) {
            send(emitter, event);
        }
        return emitter;
    }

    private void broadcast(Event event) {
        for (SseEmitter emitter : emitters) {
            send(emitter, event);
        }
    }

    private void send(SseEmitter emitter, Event event) {
        try {
            emitter.send(SseEmitter.event().name("event").data(event));
        } catch (IOException | IllegalStateException ex) {
            emitters.remove(emitter);
        }
    }
}
