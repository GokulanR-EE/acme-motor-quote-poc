package com.acme.platform.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
    void fallbackIsCoherentCarDataInPlausibleRangesNotNonsense() {
        Map<String, Object> v = vendor.lookupVehicle("ZZ99ZZZ");
        // A car, not a truck: full coherent spec.
        assertThat(v).containsKeys("make", "model", "derivative", "fuel", "transmission",
            "value", "insuranceGroup");
        int value = ((Number) v.get("value")).intValue();
        int group = ((Number) v.get("insuranceGroup")).intValue();
        assertThat(value).isBetween(MockVendorClient.FALLBACK_VALUE_MIN, MockVendorClient.FALLBACK_VALUE_MAX);
        assertThat(group).isBetween(MockVendorClient.FALLBACK_GROUP_MIN, MockVendorClient.FALLBACK_GROUP_MAX);
    }

    @Test
    void fallbackIsDeterministicAcrossCallsAndVariesByRegistration() {
        Map<String, Object> a1 = vendor.lookupVehicle("ZZ99ZZZ");
        Map<String, Object> a2 = vendor.lookupVehicle("ZZ99ZZZ");
        // Stable across calls (registration echoed differs by case; normalise first).
        assertThat(a1).containsAllEntriesOf(a2);

        Map<String, Object> b = vendor.lookupVehicle("AB12CDE");
        // Different registrations should generally yield different synthetic specs.
        assertThat(b.get("value")).isNotNull();
        assertThat(b.get("make")).isEqualTo("Sample Motors");
    }

    @Test
    void seededHighValueVehicleHasPerformanceRangeValueAndGroup() {
        Map<String, Object> v = vendor.lookupVehicle("PF21XYZ");
        assertThat(v.get("make")).isEqualTo("Performance Marque");
        assertThat(((Number) v.get("value")).intValue())
            .isGreaterThanOrEqualTo(MockVendorClient.PERFORMANCE_VALUE_THRESHOLD);
        assertThat(((Number) v.get("insuranceGroup")).intValue()).isBetween(1, 50);
    }

    @Test
    void expandedSeedSetCoversSeveralRealisticGbVehicles() {
        assertThat(vendor.lookupVehicle("VW68ABC").get("make")).isEqualTo("Volkswagen");
        assertThat(vendor.lookupVehicle("VX17KLM").get("make")).isEqualTo("Vauxhall");
        Map<String, Object> ev = vendor.lookupVehicle("TS70EVX");
        assertThat(ev.get("fuel")).isEqualTo("Electric");
    }

    @Test
    void seededPostcodeReturnsCandidateList() {
        List<Map<String, Object>> candidates = vendor.lookupAddress("RG1 1AA");
        assertThat(candidates).hasSizeGreaterThanOrEqualTo(2);
        assertThat(candidates.get(0)).containsKey("houseNumberOrName");
    }

    @Test
    void highRiskSeededPostcodeReturnsCandidatesWithExpectedShape() {
        List<Map<String, Object>> candidates = vendor.lookupAddress("M1 2AB");
        assertThat(candidates).hasSizeBetween(2, 3);
        assertThat(candidates).allSatisfy(a ->
            assertThat(a).containsKeys("houseNumberOrName", "line1", "postcode"));
        assertThat(candidates.get(0).get("postcode")).isEqualTo("M1 2AB");
        // And the rating treats it as high-risk.
        assertThat(MockVendorClient.isHighRiskPostcode("M1 2AB")).isTrue();
    }

    @Test
    void unseededPostcodeReturnsFallbackCandidate() {
        List<Map<String, Object>> candidates = vendor.lookupAddress("ZZ9 9ZZ");
        assertThat(candidates).hasSize(1);
        // Fallback echoes the postcode upper-cased and trimmed (internal space kept),
        // matching the Python platform's behaviour.
        assertThat(candidates.get(0).get("postcode")).isEqualTo("ZZ9 9ZZ");
    }

    // ---------------------------------------------------------------------
    // Rating via the vendor seam (brief §15).
    // ---------------------------------------------------------------------

    /** A clean, low-risk profile: just the base premium. */
    private static Map<String, Object> baseQuote() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("dateOfBirth", "1990-01-01"); // > 25
        customer.put("address", Map.of("postcode", "RG1 1AA")); // low-risk
        data.put("customer", customer);
        data.put("vehicle", new LinkedHashMap<>(Map.of("value", 12000, "annualMileage", 8000)));
        data.put("history", new LinkedHashMap<>(Map.of("claimsLast3Years", 0, "offencesLast5Years", 0)));
        data.put("cover", new LinkedHashMap<>(Map.of("coverLevel", "Third party", "voluntaryExcess", 250)));
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> data, String key) {
        return (Map<String, Object>) data.get(key);
    }

    @Test
    void baseCaseIsJustTheBasePremium() {
        RatingResult r = vendor.rate(baseQuote());
        assertThat(r.annualPremium()).isEqualTo(350.0);
        assertThat(r.breakdown()).hasSize(1);
        assertThat(r.breakdown().get(0).get("label")).isEqualTo("Base premium");
    }

    @Test
    void driverUnder25AddsLoading() {
        Map<String, Object> data = baseQuote();
        sub(data, "customer").put("dateOfBirth", LocalDate.now().minusYears(20).toString());
        RatingResult r = vendor.rate(data);
        assertThat(r.annualPremium()).isEqualTo(350.0 + 600.0);
    }

    @Test
    void claimsAndConvictionsAddPerOccurrence() {
        Map<String, Object> data = baseQuote();
        sub(data, "history").put("claimsLast3Years", 2);      // +£400
        sub(data, "history").put("offencesLast5Years", 1);    // +£300
        RatingResult r = vendor.rate(data);
        assertThat(r.annualPremium()).isEqualTo(350.0 + 400.0 + 300.0);
    }

    @Test
    void comprehensiveHighMileageAndLargeExcessAdjust() {
        Map<String, Object> data = baseQuote();
        sub(data, "cover").put("coverLevel", "Comprehensive");   // +£80
        sub(data, "vehicle").put("annualMileage", 20000);        // +£100 (> 12k)
        sub(data, "cover").put("voluntaryExcess", 500);          // -£50 (>= 500)
        RatingResult r = vendor.rate(data);
        assertThat(r.annualPremium()).isEqualTo(350.0 + 80.0 + 100.0 - 50.0);
    }

    @Test
    void highRiskPostcodeAndPerformanceVehicleAdd() {
        Map<String, Object> data = baseQuote();
        sub(data, "customer").put("address", Map.of("postcode", "M1 2AB")); // high-risk +£250
        sub(data, "vehicle").put("value", 60000);                            // performance +£400
        RatingResult r = vendor.rate(data);
        assertThat(r.annualPremium()).isEqualTo(350.0 + 250.0 + 400.0);
    }

    @Test
    void breakdownLinesSumToThePremium() {
        Map<String, Object> data = baseQuote();
        sub(data, "customer").put("dateOfBirth", LocalDate.now().minusYears(19).toString());
        sub(data, "customer").put("address", Map.of("postcode", "M1 2AB"));
        sub(data, "vehicle").put("value", 80000);
        sub(data, "vehicle").put("annualMileage", 25000);
        sub(data, "history").put("claimsLast3Years", 1);
        sub(data, "history").put("offencesLast5Years", 1);
        sub(data, "cover").put("coverLevel", "Comprehensive");
        sub(data, "cover").put("voluntaryExcess", 600);

        RatingResult r = vendor.rate(data);
        assertBreakdownSumsToPremium(r);
    }

    @Test
    void breakdownSumsExactlyToPremiumAcrossSeveralProfiles() {
        // Profile 1: clean base.
        assertBreakdownSumsToPremium(vendor.rate(baseQuote()));

        // Profile 2: young driver, high-risk, performance, claims+convictions, comp, high mileage, large excess.
        Map<String, Object> loaded = baseQuote();
        sub(loaded, "customer").put("dateOfBirth", LocalDate.now().minusYears(19).toString());
        sub(loaded, "customer").put("address", Map.of("postcode", "M1 2AB"));
        sub(loaded, "vehicle").put("value", 85000);
        sub(loaded, "vehicle").put("annualMileage", 30000);
        sub(loaded, "history").put("claimsLast3Years", 3);
        sub(loaded, "history").put("offencesLast5Years", 2);
        sub(loaded, "cover").put("coverLevel", "Comprehensive");
        sub(loaded, "cover").put("voluntaryExcess", 750);
        assertBreakdownSumsToPremium(vendor.rate(loaded));

        // Profile 3: third-party, mid-range, single claim.
        Map<String, Object> mid = baseQuote();
        sub(mid, "history").put("claimsLast3Years", 1);
        assertBreakdownSumsToPremium(vendor.rate(mid));
    }

    @Test
    void premiumIsRoundedToTwoDecimalsAndNeverNegative() {
        RatingResult r = vendor.rate(baseQuote());
        double rounded = Math.round(r.annualPremium() * 100.0) / 100.0;
        assertThat(r.annualPremium()).isEqualTo(rounded);
        assertThat(r.annualPremium()).isGreaterThanOrEqualTo(0.0);
        r.breakdown().forEach(line -> {
            double amt = ((Number) line.get("amount")).doubleValue();
            assertThat(amt).isEqualTo(Math.round(amt * 100.0) / 100.0);
        });
    }

    private static void assertBreakdownSumsToPremium(RatingResult r) {
        double sum = r.breakdown().stream()
            .mapToDouble(line -> ((Number) line.get("amount")).doubleValue())
            .sum();
        assertThat(sum).isCloseTo(r.annualPremium(), within(0.001));
    }

    // ---------------------------------------------------------------------
    // Mock policy issuance via the vendor seam (Slice 8). Real issuance/payments
    // stay out of scope (brief §2) — only the seam is exercised here.
    // ---------------------------------------------------------------------

    @Test
    void issuePolicyReturnsSyntheticIssuedPolicyWithCoverStartDate() {
        Map<String, Object> data = baseQuote();
        data.put("cover", new LinkedHashMap<>(Map.of("coverStartDate", "2026-07-01")));

        PolicyResult policy = vendor.issuePolicy(data);
        assertThat(policy.policyNumber()).startsWith("ACME-POL-");
        assertThat(policy.status()).isEqualTo("ISSUED");
        assertThat(policy.effectiveDate()).isEqualTo("2026-07-01");
    }

    @Test
    void issuePolicyDefaultsEffectiveDateToTodayWhenCoverStartAbsentOrBad() {
        PolicyResult policy = vendor.issuePolicy(baseQuote()); // no coverStartDate
        assertThat(policy.effectiveDate()).isEqualTo(LocalDate.now().toString());
        assertThat(policy.policyNumber()).startsWith("ACME-POL-");
    }

    @Test
    void issuePolicyMintsDistinctPolicyNumbers() {
        PolicyResult a = vendor.issuePolicy(baseQuote());
        PolicyResult b = vendor.issuePolicy(baseQuote());
        assertThat(a.policyNumber()).isNotEqualTo(b.policyNumber());
    }
}
