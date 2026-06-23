package com.acme.platform.vendor;

import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Synthetic {@link VendorClient} for the PoC. Returns mock data only — there is
 * no real vendor, brand or data behind this. Replaced by a SOAP-backed
 * implementation in production.
 */
@Component
@Primary
public class MockVendorClient implements VendorClient {

    @Override
    public Map<String, Object> systemInfo() {
        return Map.of(
                "vendor", "MOCK",
                "status", "UP",
                "note", "synthetic - not a real vendor");
    }
}
