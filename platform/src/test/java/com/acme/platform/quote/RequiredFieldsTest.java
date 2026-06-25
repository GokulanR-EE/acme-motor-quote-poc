package com.acme.platform.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Characterisation tests for {@link RequiredFields#missingFields(Map)}: nested
 * dot-path resolution, the absent-value rule (null / blank string / empty
 * container), and the stable order documented on {@link RequiredFields#MANDATORY_FIELDS}.
 */
class RequiredFieldsTest {

    @Test
    void emptyDataReturnsEveryMandatoryPath() {
        List<String> missing = RequiredFields.missingFields(Map.of());

        assertThat(missing).isEqualTo(RequiredFields.MANDATORY_FIELDS);
    }

    @Test
    void nullDataReturnsEveryMandatoryPath() {
        List<String> missing = RequiredFields.missingFields(null);

        assertThat(missing).isEqualTo(RequiredFields.MANDATORY_FIELDS);
    }

    @Test
    void fullyPopulatedModelReturnsNoMissingPaths() {
        Map<String, Object> data = fullModel();

        assertThat(RequiredFields.missingFields(data)).isEmpty();
    }

    @Test
    void partialNestedDataReportsOnlyStillMissingPaths() {
        // A vehicle section present, but with only some leaves filled.
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("registration", "AB12 CDE");
        vehicle.put("make", "Ford");
        // model, value, etc. deliberately absent

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicle", vehicle);

        List<String> missing = RequiredFields.missingFields(data);

        // The two present leaves are NOT reported...
        assertThat(missing)
            .doesNotContain("vehicle.registration", "vehicle.make")
            // ...while sibling leaves under the same nested map still are.
            .contains("vehicle.model", "vehicle.value", "vehicle.security");
    }

    @Test
    void nestedValuePresentUnderVehicleMapIsNotMissing() {
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("value", 12_000);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicle", vehicle);

        assertThat(RequiredFields.missingFields(data)).doesNotContain("vehicle.value");
    }

    @Test
    void nestedValueAbsentUnderVehicleMapIsMissing() {
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("make", "Ford"); // a sibling, but no "value"

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicle", vehicle);

        assertThat(RequiredFields.missingFields(data)).contains("vehicle.value");
    }

    @Test
    void deeplyNestedAddressPathResolvesThroughTwoLevels() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("houseNumberOrName", "10");
        address.put("postcode", "RG1 1AA");

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("address", address);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customer", customer);

        List<String> missing = RequiredFields.missingFields(data);

        assertThat(missing).doesNotContain(
            "customer.address.houseNumberOrName",
            "customer.address.postcode");
    }

    @Test
    void presentButNullLeafCountsAsMissing() {
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("value", null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicle", vehicle);

        assertThat(RequiredFields.missingFields(data)).contains("vehicle.value");
    }

    @Test
    void presentButBlankStringLeafCountsAsMissing() {
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("make", "   "); // blank/whitespace string

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicle", vehicle);

        assertThat(RequiredFields.missingFields(data)).contains("vehicle.make");
    }

    @Test
    void emptyCollectionAndEmptyMapLeavesCountAsMissing() {
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("claimsLast3Years", List.of()); // empty collection

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("history", history);

        assertThat(RequiredFields.missingFields(data)).contains("history.claimsLast3Years");
    }

    @Test
    void booleanFalseAndZeroAreRealAnswersNotMissing() {
        // Per the class contract: false and 0 are present, not absent.
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("modified", false);
        vehicle.put("imported", false);

        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("ncdYears", 0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicle", vehicle);
        data.put("driver", driver);

        List<String> missing = RequiredFields.missingFields(data);

        assertThat(missing).doesNotContain(
            "vehicle.modified", "vehicle.imported", "driver.ncdYears");
    }

    @Test
    void missingFieldsPreservesMandatoryFieldOrder() {
        // Order of the returned list is a subsequence of MANDATORY_FIELDS.
        Map<String, Object> data = fullModel();
        // Remove a few leaves so the result is a non-trivial subset.
        @SuppressWarnings("unchecked")
        Map<String, Object> vehicle = (Map<String, Object>) data.get("vehicle");
        vehicle.remove("registration");
        vehicle.remove("value");

        List<String> missing = RequiredFields.missingFields(data);

        assertThat(missing).containsExactly("vehicle.registration", "vehicle.value");

        // And generally: the result order matches the declared order.
        List<String> declaredOrderFiltered = new ArrayList<>(RequiredFields.MANDATORY_FIELDS);
        declaredOrderFiltered.retainAll(missing);
        assertThat(missing).isEqualTo(declaredOrderFiltered);
    }

    /** Build a whole-model map with every mandatory dot-path populated with a non-absent value. */
    private static Map<String, Object> fullModel() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (String path : RequiredFields.MANDATORY_FIELDS) {
            put(root, path, "x");
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String dotPath, Object value) {
        String[] parts = dotPath.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(
                parts[i], k -> new LinkedHashMap<String, Object>());
        }
        current.put(parts[parts.length - 1], value);
    }
}
