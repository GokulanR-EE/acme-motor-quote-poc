package com.acme.platform.quote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Mandatory-field spec → {@code missingFields} computation (brief §11, §6).
 *
 * <p>The backend — not the conversation layer — owns <i>what is still required</i>
 * before a quote can be priced. The mandatory ({@code M}) fields from brief §11
 * are encoded here as <b>dot-paths</b> into the whole-model payload. Collection
 * is order-free: a flat list of remaining mandatory paths is reported.
 */
public final class RequiredFields {

    private RequiredFields() {
    }

    /** Mandatory fields from brief §11, as dot-paths. Order is stable. */
    public static final List<String> MANDATORY_FIELDS = List.of(
        // Vehicle
        "vehicle.registration",
        "vehicle.make",
        "vehicle.model",
        "vehicle.datePurchased",
        "vehicle.value",
        "vehicle.useOfVehicle",
        "vehicle.security",
        "vehicle.dashcam",
        "vehicle.modified",
        "vehicle.imported",
        "vehicle.daytimeLocation",
        "vehicle.overnightLocation",
        "vehicle.annualMileage",
        "vehicle.registeredKeeper",
        "vehicle.legalOwner",
        // Customer
        "customer.title",
        "customer.firstName",
        "customer.surname",
        "customer.dateOfBirth",
        "customer.maritalStatus",
        "customer.childrenUnder16",
        "customer.employmentStatus",
        "customer.partTimeJob",
        "customer.yearsLivedInUK",
        "customer.address.houseNumberOrName",
        "customer.address.postcode",
        "customer.ownsProperty",
        "customer.carKeptOvernightAtAddress",
        "customer.email",
        // Driver
        "driver.licenceType",
        "driver.licenceHeldFor",
        "driver.insuranceCancelledOrVoid",
        "driver.ncdYears",
        "driver.ncdOnCompanyCar",
        // History
        "history.claimsLast3Years",
        "history.offencesLast5Years",
        "history.unspentCriminalConvictions",
        // Household
        "household.carsInHousehold",
        "household.anotherCarHasCover",
        "household.regularUseOfOtherVehicles",
        // Cover
        "cover.paymentMethod",
        "cover.coverLevel",
        "cover.coverStartDate",
        "cover.voluntaryExcess"
    );

    /** Walk a dot-path through nested maps. Returns the value, or {@code null} if absent. */
    @SuppressWarnings("unchecked")
    private static Object resolve(Map<String, Object> data, String path) {
        Object current = data;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    /**
     * A value is 'absent' if it is {@code null}, an empty/blank string, or an
     * empty container. Booleans (including {@code false}) and {@code 0} are
     * present — they are real answers.
     */
    private static boolean isAbsent(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.strip().isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }

    /** The mandatory dot-paths whose value is absent/empty in {@code quoteData}. */
    public static List<String> missingFields(Map<String, Object> quoteData) {
        Map<String, Object> data = quoteData == null ? Map.of() : quoteData;
        List<String> missing = new ArrayList<>();
        for (String path : MANDATORY_FIELDS) {
            if (isAbsent(resolve(data, path))) {
                missing.add(path);
            }
        }
        return missing;
    }
}
