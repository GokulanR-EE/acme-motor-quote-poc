package com.acme.platform.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MockVendorClientTest {

    private final MockVendorClient vendor = new MockVendorClient();

    @Test
    void seededRegistrationReturnsKnownVehicleEchoingRegistration() {
        Map<String, Object> v = vendor.lookupVehicle("FX19ZTC");
        assertThat(v.get("make")).isEqualTo("Ford");
        assertThat(v.get("model")).isEqualTo("Focus");
        assertThat(v.get("registration")).isEqualTo("FX19ZTC");
        assertThat(v).containsKeys("derivative", "fuel", "transmission");
    }

    @Test
    void unknownRegistrationReturnsDeterministicSyntheticFallback() {
        Map<String, Object> v = vendor.lookupVehicle("ZZ99ZZZ");
        assertThat(v.get("make")).isEqualTo("Sample Motors");
        assertThat(v.get("registration")).isEqualTo("ZZ99ZZZ");
    }

    @Test
    void seededPostcodeReturnsCandidateList() {
        List<Map<String, Object>> candidates = vendor.lookupAddress("RG1 1AA");
        assertThat(candidates).hasSizeGreaterThanOrEqualTo(2);
        assertThat(candidates.get(0)).containsKey("houseNumberOrName");
    }

    @Test
    void unseededPostcodeReturnsFallbackCandidate() {
        List<Map<String, Object>> candidates = vendor.lookupAddress("ZZ9 9ZZ");
        assertThat(candidates).hasSize(1);
        // Fallback echoes the postcode upper-cased and trimmed (internal space kept),
        // matching the Python platform's behaviour.
        assertThat(candidates.get(0).get("postcode")).isEqualTo("ZZ9 9ZZ");
    }
}
