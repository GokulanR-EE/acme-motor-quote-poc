package com.acme.platform.vendor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, synthetic {@link VendorClient} for the PoC — no real brand,
 * plate, or vehicle data anywhere (brief naming rule). This is the <b>reference
 * implementation</b> of the vendor seam: the future {@link SoapVendorClient}
 * returns the same shapes from real WSDL calls, so the data here is kept
 * production-quality-as-a-mock — <b>coherent and never random</b>.
 *
 * <h2>Coherence guarantees</h2>
 * <ul>
 *   <li><b>Vehicle lookup</b> returns a plausible GB car (make / model /
 *       derivative / fuel / transmission / value / insurance group). A seeded
 *       registration returns its known vehicle; an unknown registration returns
 *       a <b>deterministic</b> fallback <i>car</i> (never a truck for a car, never
 *       {@code null}) derived from the registration, stable across calls and in
 *       plausible ranges.</li>
 *   <li><b>Address lookup</b> returns 2–3 candidate addresses for a seeded
 *       postcode, or a single deterministic fallback candidate otherwise.</li>
 *   <li><b>Rating</b> (brief §15) responds sensibly to inputs and returns a
 *       {@code {label, amount}} breakdown whose lines <b>sum exactly</b> to the
 *       annual premium (both rounded to 2dp); the premium is never negative.</li>
 * </ul>
 *
 * <p>Active by default (the {@code mock-vendor} seam, {@code platform.vendor=mock}
 * or unset); {@link SoapVendorClient} is the {@code soap} variant behind the same
 * interface, so swapping mock&rarr;SOAP is config-only.
 */
@Component
@ConditionalOnProperty(name = "platform.vendor", havingValue = "mock", matchIfMissing = true)
public class MockVendorClient implements VendorClient {

    // =====================================================================
    // Seeded synthetic data
    // =====================================================================

    /**
     * Seeded synthetic vehicles spanning the value / insurance-group range so a
     * demo is rich: an everyday hatchback, a family hatchback, a small city car,
     * an EV, and a high-value performance car (drives the §15 performance loading
     * and the underwriting refer). Keys are normalised registrations.
     */
    private static final Map<String, Map<String, Object>> SEEDED_VEHICLES = new LinkedHashMap<>();

    /**
     * Seeded synthetic postcodes, each with 2–3 candidate addresses. Includes a
     * high-risk outward area ({@code M1}) used by the rating high-risk loading.
     */
    private static final Map<String, List<Map<String, Object>>> SEEDED_ADDRESSES = new LinkedHashMap<>();

    // =====================================================================
    // Mock rating model (brief §15) — deliberately simple and transparent.
    // Each constant is a documented rule; none come from real rating material.
    // =====================================================================

    /** Starting point every quote builds on. */
    static final double BASE_PREMIUM = 350.0;
    /** Young-driver loading (higher claims frequency), applied when age < 25. */
    static final double LOADING_UNDER_25 = 600.0;
    /** Loading for a high-risk postcode (theft / accident hotspot heuristic). */
    static final double LOADING_HIGH_RISK_POSTCODE = 250.0;
    /** Loading for a performance / high-value vehicle. */
    static final double LOADING_PERFORMANCE_VEHICLE = 400.0;
    /** Per-claim loading (prior at-fault history). */
    static final double LOADING_PER_CLAIM = 200.0;
    /** Per-conviction loading (motoring offences). */
    static final double LOADING_PER_CONVICTION = 300.0;
    /** Loading for comprehensive cover vs third-party. */
    static final double LOADING_COMPREHENSIVE = 80.0;
    /** Loading for high annual mileage (more exposure). */
    static final double LOADING_HIGH_MILEAGE = 100.0;
    /** Discount for accepting a large voluntary excess (lower small-claim risk). */
    static final double DISCOUNT_LARGE_EXCESS = 50.0;

    // Mock thresholds (documented, not from real rating material; brief §15).
    /** £ — "performance vehicle" heuristic: value at/above this. */
    static final int PERFORMANCE_VALUE_THRESHOLD = 60_000;
    /** miles/year — "high mileage" above this. */
    static final int HIGH_MILEAGE_THRESHOLD = 12_000;
    /** £ voluntary excess at/above this earns the large-excess discount. */
    static final int LARGE_EXCESS_THRESHOLD = 500;

    /** Mock high-risk postcode outward areas (theft / accident hotspot heuristic). */
    static final Set<String> HIGH_RISK_POSTCODE_PREFIXES = Set.of("M1", "B1", "L1", "BD1", "BB1");

    // =====================================================================
    // Deterministic fallback vehicle ranges (coherent, plausible — a car).
    // =====================================================================

    /** Neutral synthetic make for an unknown registration (no real brand). */
    static final String FALLBACK_MAKE = "Sample Motors";
    /** Synthetic model line-up the fallback picks from, deterministically by reg. */
    static final List<String[]> FALLBACK_MODELS = List.of(
        // {model, derivative, fuel, transmission}
        new String[]{"Saloon", "Standard 1.6", "Petrol", "Manual"},
        new String[]{"Hatch", "SE 1.2", "Petrol", "Manual"},
        new String[]{"Estate", "SE 1.5 TDI", "Diesel", "Manual"},
        new String[]{"Crossover", "Sport 1.4", "Petrol", "Automatic"},
        new String[]{"EV", "Electric 40kWh", "Electric", "Automatic"}
    );
    /** Plausible fallback value band (£): a typical used family car, never absurd. */
    static final int FALLBACK_VALUE_MIN = 5_000;
    static final int FALLBACK_VALUE_MAX = 25_000;
    /** Plausible fallback insurance group band (1–50 scale in GB). */
    static final int FALLBACK_GROUP_MIN = 5;
    static final int FALLBACK_GROUP_MAX = 30;

    static {
        // make, model, derivative, fuel, transmission, value(£), insuranceGroup(1-50)
        SEEDED_VEHICLES.put("FX19ZTC",
            vehicle("Ford", "Focus", "Titanium 1.0 EcoBoost", "Petrol", "Manual", 12_000, 14));
        SEEDED_VEHICLES.put("VW68ABC",
            vehicle("Volkswagen", "Golf", "Life 1.5 TSI", "Petrol", "Automatic", 18_500, 18));
        SEEDED_VEHICLES.put("VX17KLM",
            vehicle("Vauxhall", "Corsa", "SRi 1.2", "Petrol", "Manual", 8_000, 9));
        SEEDED_VEHICLES.put("TS70EVX",
            vehicle("Tesla", "Model 3", "Long Range", "Electric", "Automatic", 42_000, 48));
        SEEDED_VEHICLES.put("PF21XYZ",
            vehicle("Performance Marque", "GT Coupe", "Twin-Turbo 600", "Petrol", "Automatic", 85_000, 50));

        SEEDED_ADDRESSES.put("RG11AA", List.of(
            address("1", "1 Sample Street", "RG1 1AA"),
            address("2", "2 Sample Street", "RG1 1AA"),
            address("3", "3 Sample Street", "RG1 1AA")
        ));
        // High-risk outward area (M1) — exercised by the rating high-risk loading.
        SEEDED_ADDRESSES.put("M12AB", List.of(
            address("10", "10 Example Road", "M1 2AB"),
            address("12", "12 Example Road", "M1 2AB")
        ));
        SEEDED_ADDRESSES.put("EH11AB", List.of(
            address("4", "4 Synthetic Crescent", "EH1 1AB"),
            address("6", "6 Synthetic Crescent", "EH1 1AB")
        ));
    }

    private static Map<String, Object> vehicle(String make, String model, String derivative,
                                               String fuel, String transmission,
                                               int value, int insuranceGroup) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("make", make);
        v.put("model", model);
        v.put("derivative", derivative);
        v.put("fuel", fuel);
        v.put("transmission", transmission);
        v.put("value", value);
        v.put("insuranceGroup", insuranceGroup);
        return v;
    }

    private static Map<String, Object> address(String houseNumberOrName, String line1, String postcode) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("houseNumberOrName", houseNumberOrName);
        a.put("line1", line1);
        a.put("postcode", postcode);
        return a;
    }

    private static String normaliseReg(String registration) {
        return (registration == null ? "" : registration).toUpperCase().replace(" ", "");
    }

    private static String normalisePostcode(String postcode) {
        return (postcode == null ? "" : postcode).toUpperCase().replace(" ", "");
    }

    // =====================================================================
    // Vehicle lookup
    // =====================================================================

    @Override
    public Map<String, Object> lookupVehicle(String registration) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registration", registration);
        Map<String, Object> seeded = SEEDED_VEHICLES.get(normaliseReg(registration));
        result.putAll(seeded != null ? seeded : fallbackVehicle(registration));
        return result;
    }

    /**
     * Deterministic synthetic fallback for an unknown registration: always a
     * plausible <b>car</b> (never a truck), stable across calls (derived purely
     * from the registration), with value and insurance group in believable bands.
     * Demos always have a make/model to show.
     */
    private static Map<String, Object> fallbackVehicle(String registration) {
        int seed = stableSeed(normaliseReg(registration));
        String[] spec = FALLBACK_MODELS.get(seed % FALLBACK_MODELS.size());
        int value = bounded(seed, FALLBACK_VALUE_MIN, FALLBACK_VALUE_MAX, 500);
        int group = bounded(seed / 7, FALLBACK_GROUP_MIN, FALLBACK_GROUP_MAX, 1);
        return vehicle(FALLBACK_MAKE, spec[0], spec[1], spec[2], spec[3], value, group);
    }

    /** Non-negative stable hash of a key (deterministic across JVMs for ASCII regs). */
    private static int stableSeed(String key) {
        int h = 0;
        for (int i = 0; i < key.length(); i++) {
            h = 31 * h + key.charAt(i);
        }
        return h & 0x7fffffff;
    }

    /** Map a seed into {@code [min, max]} on the given {@code step} grid (inclusive, plausible). */
    private static int bounded(int seed, int min, int max, int step) {
        int span = (max - min) / step + 1;
        return min + (seed % span) * step;
    }

    // =====================================================================
    // Address lookup
    // =====================================================================

    @Override
    public List<Map<String, Object>> lookupAddress(String postcode) {
        List<Map<String, Object>> seeded = SEEDED_ADDRESSES.get(normalisePostcode(postcode));
        if (seeded != null) {
            return new ArrayList<>(seeded);
        }
        // Deterministic synthetic fallback candidate; echoes the postcode
        // upper-cased and trimmed (internal space kept) for downstream parity.
        String normalised = (postcode == null ? "" : postcode).strip().toUpperCase();
        List<Map<String, Object>> fallback = new ArrayList<>();
        fallback.add(address("1", "1 Synthetic Avenue", normalised));
        return fallback;
    }

    // =====================================================================
    // Rating (brief §15)
    // =====================================================================

    /**
     * Deterministic mock rating per brief §15. Starts at the base premium and
     * applies each adjustment, recording a {@code {label, amount}} breakdown line
     * (rounded to 2dp) for every non-zero step. The premium is the rounded sum of
     * those lines, clamped to be never negative, so by construction the breakdown
     * <b>sums exactly</b> to the returned premium and the conversation can explain
     * the number without inventing anything.
     *
     * <p>This is the value a real insurer would obtain from the vendor over SOAP;
     * a future {@code SoapVendorClient.rate(...)} would return the same
     * {@link RatingResult} shape.
     */
    @Override
    public RatingResult rate(Map<String, Object> quoteData) {
        Map<String, Object> data = quoteData == null ? Map.of() : quoteData;
        Map<String, Object> vehicle = section(data, "vehicle");
        Map<String, Object> customer = section(data, "customer");
        Map<String, Object> history = section(data, "history");
        Map<String, Object> cover = section(data, "cover");

        List<Map<String, Object>> breakdown = new ArrayList<>();
        breakdown.add(line("Base premium", BASE_PREMIUM));

        Integer age = QuoteValues.ageFromDob(customer.get("dateOfBirth"));
        if (age != null && age < 25) {
            breakdown.add(line("Driver under 25", LOADING_UNDER_25));
        }

        if (isHighRiskPostcode(postcode(customer))) {
            breakdown.add(line("High-risk postcode", LOADING_HIGH_RISK_POSTCODE));
        }

        if (isPerformanceVehicle(vehicle)) {
            breakdown.add(line("Performance vehicle", LOADING_PERFORMANCE_VEHICLE));
        }

        int claims = QuoteValues.intValue(history.get("claimsLast3Years"), 0);
        if (claims > 0) {
            breakdown.add(line(claims + " claim(s) in last 3 years", LOADING_PER_CLAIM * claims));
        }

        int convictions = QuoteValues.intValue(history.get("offencesLast5Years"), 0);
        if (convictions > 0) {
            breakdown.add(line(convictions + " conviction(s) in last 5 years",
                LOADING_PER_CONVICTION * convictions));
        }

        if (isComprehensive(cover)) {
            breakdown.add(line("Comprehensive cover", LOADING_COMPREHENSIVE));
        }

        int mileage = QuoteValues.intValue(vehicle.get("annualMileage"), 0);
        if (mileage > HIGH_MILEAGE_THRESHOLD) {
            breakdown.add(line("High annual mileage", LOADING_HIGH_MILEAGE));
        }

        int voluntaryExcess = QuoteValues.intValue(cover.get("voluntaryExcess"), 0);
        if (voluntaryExcess >= LARGE_EXCESS_THRESHOLD) {
            breakdown.add(line("Large voluntary excess discount", -DISCOUNT_LARGE_EXCESS));
        }

        // Premium is the (clamped, rounded) sum of the breakdown lines — so the
        // lines always sum exactly to the returned premium.
        double premium = Math.max(0.0, breakdown.stream()
            .mapToDouble(l -> ((Number) l.get("amount")).doubleValue())
            .sum());
        return new RatingResult(round2(premium), breakdown);
    }

    // =====================================================================
    // Policy issuance (Slice 8)
    // =====================================================================

    /**
     * Issue a deterministic, synthetic policy (Slice 8): a policy number with the
     * neutral {@code ACME-POL-} prefix plus a short id, status {@code ISSUED}, and
     * an effective date taken from the quote's cover start date (or today if
     * absent/unparseable). No real brand, plate, or vehicle data.
     *
     * <p>This is the value a real insurer would obtain from the vendor over SOAP;
     * a future {@code SoapVendorClient.issuePolicy(...)} would return the same
     * {@link PolicyResult} shape. <b>Real issuance and payments stay out of scope
     * (brief §2)</b> — only this seam is visible.
     */
    @Override
    public PolicyResult issuePolicy(Map<String, Object> quoteData) {
        Map<String, Object> data = quoteData == null ? Map.of() : quoteData;
        Map<String, Object> cover = section(data, "cover");

        String effectiveDate = coverStartDate(cover.get("coverStartDate"));
        String shortId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PolicyResult("ACME-POL-" + shortId, "ISSUED", effectiveDate);
    }

    /** The cover start date if it parses as ISO {@code yyyy-MM-dd}, else today. */
    private static String coverStartDate(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            try {
                return LocalDate.parse(s.strip()).toString();
            } catch (DateTimeParseException ignored) {
                // fall through to today
            }
        }
        return LocalDate.now().toString();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return (v instanceof Map) ? (Map<String, Object>) v : Map.of();
    }

    /** A breakdown line {@code {label, amount}} with the amount rounded to 2dp. */
    private static Map<String, Object> line(String label, double amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("amount", round2(amount));
        return m;
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String postcode(Map<String, Object> customer) {
        Object addr = customer.get("address");
        if (addr instanceof Map<?, ?> m) {
            Object pc = m.get("postcode");
            return pc == null ? null : pc.toString();
        }
        return null;
    }

    static boolean isHighRiskPostcode(String postcode) {
        if (postcode == null) {
            return false;
        }
        String outward = normalisePostcode(postcode);
        // Outward code is everything before the final three (inward) characters.
        if (outward.length() > 3) {
            outward = outward.substring(0, outward.length() - 3);
        }
        return HIGH_RISK_POSTCODE_PREFIXES.contains(outward);
    }

    /** Mock heuristic: a flagged performance vehicle, or value at/above the threshold. */
    static boolean isPerformanceVehicle(Map<String, Object> vehicle) {
        Object flag = vehicle.get("performance");
        if (flag instanceof Boolean b && b) {
            return true;
        }
        return QuoteValues.intValue(vehicle.get("value"), 0) >= PERFORMANCE_VALUE_THRESHOLD;
    }

    private static boolean isComprehensive(Map<String, Object> cover) {
        Object level = cover.get("coverLevel");
        return level != null && level.toString().toLowerCase().contains("comprehensive");
    }
}
