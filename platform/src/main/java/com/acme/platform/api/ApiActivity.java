package com.acme.platform.api;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.acme.platform.events.EventStore;

/**
 * The reusable "API layer logs request + response" primitive of the three-layer
 * discipline: {@code tool -> API (logged here) -> state mutation + domain event}.
 *
 * <p>Every API entry point records an {@code API_CALL} event (category "api")
 * capturing the API name, the request and the response, which feeds the
 * dashboard's API Activity view.
 */
@Component
public class ApiActivity {

    private final EventStore eventStore;

    public ApiActivity(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    /**
     * Record an API call as an event in the "api" category.
     *
     * @param api      the API name (e.g. "ping")
     * @param request  the request payload (may be null)
     * @param response the response payload
     */
    public void record(String api, Object request, Object response) {
        eventStore.append(
                "API_CALL",
                Map.of(
                        "api", api,
                        "request", request == null ? Map.of() : request,
                        "response", response),
                "api");
    }
}
