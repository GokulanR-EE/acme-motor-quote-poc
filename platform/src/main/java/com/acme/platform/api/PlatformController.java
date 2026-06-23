package com.acme.platform.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.acme.platform.events.EventStore;
import com.acme.platform.vendor.VendorClient;

/**
 * Slice 1 REST surface. Demonstrates the three-layer discipline end to end:
 * the API call is logged via {@link ApiActivity} and a domain event is appended
 * to the {@link EventStore}, while the vendor seam is exercised via
 * {@link VendorClient}.
 */
@RestController
public class PlatformController {

    private final EventStore eventStore;
    private final ApiActivity apiActivity;
    private final VendorClient vendorClient;

    public PlatformController(EventStore eventStore, ApiActivity apiActivity, VendorClient vendorClient) {
        this.eventStore = eventStore;
        this.apiActivity = apiActivity;
        this.vendorClient = vendorClient;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/ping")
    public Map<String, Object> ping(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pong", true);
        result.put("echo", body == null ? Map.of() : body);
        result.put("vendor", vendorClient.systemInfo());

        // Layer 2: API call logged (request + response).
        apiActivity.record("ping", body, result);
        // Layer 3: state change / domain event.
        eventStore.append("PING", Map.of("echo", body == null ? Map.of() : body), "domain");

        return result;
    }
}
