package com.acme.platform.events;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * API-layer primitive of the three-layer discipline: log an API call's request
 * and response. Appends an {@code API_CALL} event (category {@code "api"}) so
 * every call crossing the platform boundary is observable on the live channel.
 */
@Component
public class ApiActivity {

    private final EventStore store;

    public ApiActivity(EventStore store) {
        this.store = store;
    }

    public void record(String api, Object request, Object response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api", api);
        payload.put("request", request);
        payload.put("response", response);
        store.append("API_CALL", payload, "api");
    }
}
