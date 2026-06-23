package com.acme.platform.web;

import java.io.IOException;
import java.util.function.Consumer;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.acme.platform.events.Event;
import com.acme.platform.events.EventStore;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Live event channel over Server-Sent Events. Replays the full history (so a
 * freshly connected dashboard is fully caught up) and then streams new events
 * as they are appended to the store.
 *
 * <p>Wire format matches the Python platform exactly: each event is emitted as
 * an SSE {@code data:} frame whose body is the JSON of {@code Event.toMap()}.
 * Served same-origin at {@code /events} so the dashboard's
 * {@code new EventSource("/events")} works unchanged.
 */
@RestController
public class EventsController {

    private final EventStore store;
    private final ObjectMapper mapper;

    public EventsController(EventStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(0L); // never time out

        // Holder so the sender lambda can reference itself to unsubscribe on error.
        @SuppressWarnings("unchecked")
        Consumer<Event>[] self = new Consumer[1];
        Consumer<Event> sender = event -> {
            try {
                emitter.send(SseEmitter.event().data(mapper.writeValueAsString(event.toMap())));
            } catch (IOException e) {
                // Client disconnected — drop the subscriber and complete.
                store.unsubscribe(self[0]);
                emitter.complete();
            } catch (RuntimeException e) {
                emitter.completeWithError(e);
            }
        };
        self[0] = sender;

        store.subscribeWithReplay(sender);

        Runnable cleanup = () -> store.unsubscribe(sender);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
        return emitter;
    }
}
