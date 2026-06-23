package com.acme.platform.channel;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.acme.platform.events.Event;
import com.acme.platform.events.EventStore;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Live WebSocket channel at {@code /ws}. On connect, replays every existing event
 * as JSON, then streams newly appended events. Registers itself as a listener on
 * the {@link EventStore} so appends are broadcast to all open sessions.
 */
@Component
public class EventWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final EventStore eventStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventWebSocketHandler(EventStore eventStore) {
        this.eventStore = eventStore;
        // Stream subsequently-appended events to every open session.
        eventStore.addListener(this::broadcast);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        // Replay existing events on connect.
        for (Event event : eventStore.all()) {
            send(session, event);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private void broadcast(Event event) {
        for (WebSocketSession session : sessions) {
            send(session, event);
        }
    }

    private void send(WebSocketSession session, Event event) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException ex) {
            sessions.remove(session);
        }
    }
}
