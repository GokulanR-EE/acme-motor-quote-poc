package com.acme.platform.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.acme.platform.events.ApiActivity;
import com.acme.platform.quote.DemoSeeder;
import com.acme.platform.quote.QuoteService;
import com.acme.platform.vendor.VendorClient;

/**
 * The platform's REST surface. Preserves the exact contract of the Python
 * platform so the unchanged MCP server, conversation backend, and dashboard
 * keep working.
 *
 * <p>Three-layer discipline: each route logs an {@code API_CALL} (request +
 * response) via {@link ApiActivity#record}, then the service mutates state and
 * appends a {@code QUOTE_*} domain event.
 */
@RestController
public class PlatformController {

    private final QuoteService quotes;
    private final VendorClient vendor;
    private final ApiActivity api;
    private final DemoSeeder demo;

    public PlatformController(QuoteService quotes, VendorClient vendor, ApiActivity api, DemoSeeder demo) {
        this.quotes = quotes;
        this.vendor = vendor;
        this.api = api;
        this.demo = demo;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/ping")
    public Map<String, Object> ping(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pong", true);
        result.put("echo", request);

        // API layer: log request + response.
        api.record("ping", request, result);
        // Domain layer: mutate state (none yet) + append a domain event.
        // (Kept for three-layer parity with the Python platform.)
        return result;
    }

    /** Create a draft quote. Returns quoteId, sessionId, journeyState, missingFields. */
    @PostMapping("/quotes")
    public ResponseEntity<Map<String, Object>> postQuote() {
        Map<String, Object> result = quotes.createQuote(); // emits QUOTE_CREATED (domain)
        // API-layer log must not leak the sessionId beyond the creator.
        Map<String, Object> logged = new LinkedHashMap<>(result);
        logged.remove("sessionId");
        api.record("create_quote", Map.of(), logged);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** Retrieve a quote. Requires X-Session-Id; 404 on unknown id or mismatch. */
    @GetMapping("/quotes/{quoteId}")
    public Map<String, Object> getQuote(
        @PathVariable String quoteId,
        @RequestHeader(name = "X-Session-Id", required = false) String sessionId
    ) {
        if (DemoSeeder.DEMO_QUOTE_ID.equals(quoteId)) {
            // Self-seed the stable demo quote on first access (brief §9, §17.7).
            demo.ensureSeeded();
        }
        Map<String, Object> state = quotes.getQuote(quoteId, sessionId == null ? "" : sessionId);
        api.record("get_quote", Map.of("quoteId", quoteId), state != null ? state : Map.of("error", "not_found"));
        if (state == null) {
            // Do not reveal whether the quote exists.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found");
        }
        return state;
    }

    /** Apply a partial patch. Body: {"patch": {...}}. 404 on mismatch. */
    @PatchMapping("/quotes/{quoteId}")
    public Map<String, Object> patchQuote(
        @PathVariable String quoteId,
        @RequestBody(required = false) Map<String, Object> body,
        @RequestHeader(name = "X-Session-Id", required = false) String sessionId
    ) {
        Object rawPatch = body == null ? null : body.get("patch");
        @SuppressWarnings("unchecked")
        Map<String, Object> patch = (rawPatch instanceof Map) ? (Map<String, Object>) rawPatch : Map.of();
        Map<String, Object> state = quotes.applyPatch(quoteId, sessionId == null ? "" : sessionId, patch);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("quoteId", quoteId);
        req.put("patch", patch);
        api.record("update_quote", req, state != null ? state : Map.of("error", "not_found"));
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found");
        }
        return state;
    }

    /** Vehicle lookup via the vendor SOAP seam (no session required). */
    @GetMapping("/vehicles/{registration}")
    public Map<String, Object> lookupVehicle(@PathVariable String registration) {
        Map<String, Object> result = vendor.lookupVehicle(registration);
        api.record("lookup_vehicle", Map.of("registration", registration), result);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
        return result;
    }

    /** Address candidates via the vendor SOAP seam (no session required). */
    @GetMapping("/addresses")
    public Map<String, Object> lookupAddress(@RequestParam String postcode) {
        List<Map<String, Object>> candidates = vendor.lookupAddress(postcode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postcode", postcode);
        result.put("candidates", candidates);
        api.record("lookup_address", Map.of("postcode", postcode), result);
        return result;
    }
}
