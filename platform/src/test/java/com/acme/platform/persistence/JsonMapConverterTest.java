package com.acme.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link JsonMapConverter}: round-trip fidelity for nested
 * maps, null/blank handling on both directions, LinkedHashMap insertion-order
 * preservation, and the declared {@link IllegalStateException} on malformed JSON.
 */
class JsonMapConverterTest {

    private final JsonMapConverter converter = new JsonMapConverter();

    @Test
    void roundTripsNestedMapAndList() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("postcode", "RG1 1AA");
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("address", address);

        Map<String, Object> original = new LinkedHashMap<>();
        original.put("customer", customer);
        original.put("items", List.of(1, 2));

        String column = converter.convertToDatabaseColumn(original);
        Map<String, Object> back = converter.convertToEntityAttribute(column);

        assertThat(back).isEqualTo(original);
        // Verify nested resolution survives explicitly.
        assertThat(back)
            .extracting("customer")
            .extracting("address")
            .extracting("postcode")
            .isEqualTo("RG1 1AA");
        assertThat(back.get("items")).isEqualTo(List.of(1, 2));
    }

    @Test
    void nullAttributeSerialisesToNullColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void nullColumnDeserialisesToEmptyMap() {
        Map<String, Object> result = converter.convertToEntityAttribute(null);

        assertThat(result).isNotNull().isEmpty();
        assertThat(result).isInstanceOf(LinkedHashMap.class);
    }

    @Test
    void blankColumnDeserialisesToEmptyMap() {
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void insertionOrderIsPreservedAcrossRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("z", 1);
        original.put("a", 2);
        original.put("m", 3);

        Map<String, Object> back = converter.convertToEntityAttribute(
            converter.convertToDatabaseColumn(original));

        assertThat(back.keySet()).containsExactly("z", "a", "m");
        assertThat(back).isInstanceOf(LinkedHashMap.class);
    }

    @Test
    void emptyMapRoundTripsToEmptyMap() {
        String column = converter.convertToDatabaseColumn(new LinkedHashMap<>());
        assertThat(converter.convertToEntityAttribute(column)).isEmpty();
    }

    @Test
    void malformedJsonOnReadThrowsIllegalStateException() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{not valid json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("deserialise");
    }
}
