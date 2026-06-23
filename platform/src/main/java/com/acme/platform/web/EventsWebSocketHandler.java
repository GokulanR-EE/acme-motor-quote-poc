package com.acme.platform.web;

import java.io.IOException;
import java.util.function.Consumer;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.acme.platform.events.Event;
import com.acme.platform.events.EventStore;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Live event channel over WebSocket at {@code /ws}. Same behaviour as the SSE
 * channel: replay history on connect, then stream new events. Each event is
 * sent as a JSON text frame of {@code Event.toMap()}.
 */
public class EventsWebSocketHandler extends TextWebSocketHandler {

    private final EventStore store;
    private final ObjectMapper mapper;

    // Per-session subscriber, so we can unregister it on disconnect.
    private static final String ATTR_SUBSCRIBER = "subscriber";

    public EventsWebSocketHandler(EventStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Consumer<Event> sender = event -> send(session, event);
        session.getAttributes().put(ATTR_SUBSCRIBER, sender);
        store.subscribeWithReplay(sender);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object sub = session.getAttributes().remove(ATTR_SUBSCRIBER);
        if (sub != null) {
            store.unsubscribe((Consumer<Event>) sub);
        }
    }

    private void send(WebSocketSession session, Event event) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(event.toMap())));
                }
            }
        } catch (IOException e) {
            // Drop on send failure; the close handler cleans up the subscription.
        }
    }
}
