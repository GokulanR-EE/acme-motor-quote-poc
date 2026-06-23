package com.acme.platform.vendor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Deterministic, synthetic {@link VendorClient} for the PoC — no real brand,
 * plate, or vehicle data anywhere (brief naming rule).
 *
 * <p>Design decision (documented): for an <b>unknown registration</b> the mock
 * returns a deterministic synthetic fallback vehicle (never {@code null}) so
 * demos always have a make/model to show; address lookup likewise returns a
 * deterministic candidate list for unseeded postcodes.
 *
 * <p>{@code @Primary} so it is injected by default; the future
 * {@code SoapVendorClient} will replace it behind the same interface.
 */
@Component
@Primary
public class MockVendorClient implements VendorClient {

    // Seeded synthetic registrations: one ordinary car, plus a performance /
    // high-value car used later for the referral demo (brief §15).
    private static final Map<String, Map<String, Object>> SEEDED_VEHICLES = new LinkedHashMap<>();
    private static final Map<String, List<Map<String, Object>>> SEEDED_ADDRESSES = new LinkedHashMap<>();

    static {
        SEEDED_VEHICLES.put("FX19ZTC", vehicle("Ford", "Focus", "Titanium 1.0 EcoBoost", "Petrol", "Manual"));
        SEEDED_VEHICLES.put("VW68ABC", vehicle("Volkswagen", "Golf", "Life 1.5 TSI", "Petrol", "Automatic"));
        SEEDED_VEHICLES.put("PF21XYZ", vehicle("Performance Marque", "GT Coupe", "Twin-Turbo 600", "Petrol", "Automatic"));

        SEEDED_ADDRESSES.put("RG11AA", List.of(
            address("1", "1 Sample Street", "RG1 1AA"),
            address("2", "2 Sample Street", "RG1 1AA"),
            address("3", "3 Sample Street", "RG1 1AA")
        ));
        SEEDED_ADDRESSES.put("M12AB", List.of(
            address("10", "10 Example Road", "M1 2AB"),
            address("12", "12 Example Road", "M1 2AB")
        ));
    }

    private static Map<String, Object> vehicle(String make, String model, String derivative, String fuel, String transmission) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("make", make);
        v.put("model", model);
        v.put("derivative", derivative);
        v.put("fuel", fuel);
        v.put("transmission", transmission);
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

    @Override
    public Map<String, Object> lookupVehicle(String registration) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registration", registration);
        Map<String, Object> seeded = SEEDED_VEHICLES.get(normaliseReg(registration));
        if (seeded != null) {
            result.putAll(seeded);
        } else {
            // Deterministic synthetic fallback so a demo always has a make/model.
            result.putAll(vehicle("Sample Motors", "Saloon", "Standard", "Petrol", "Manual"));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> lookupAddress(String postcode) {
        List<Map<String, Object>> seeded = SEEDED_ADDRESSES.get(normalisePostcode(postcode));
        if (seeded != null) {
            return new ArrayList<>(seeded);
        }
        // Deterministic synthetic fallback candidate.
        String normalised = (postcode == null ? "" : postcode).strip().toUpperCase();
        List<Map<String, Object>> fallback = new ArrayList<>();
        fallback.add(address("1", "1 Synthetic Avenue", normalised));
        return fallback;
    }
}
