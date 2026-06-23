package com.acme.platform.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.acme.platform.events.EventStore;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Registers the live event WebSocket at {@code /ws} (any origin, like the SSE channel). */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EventStore store;
    private final ObjectMapper mapper;

    public WebSocketConfig(EventStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EventsWebSocketHandler(store, mapper), "/ws").setAllowedOrigins("*");
    }
}
