package com.acme.platform.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter that serialises a whole-model {@code Map<String,Object>}
 * to a JSON string column and back via Jackson. Used for the quote data, pricing,
 * and policy JSON columns, and the event payload.
 *
 * <p>Stored as a JSON/CLOB text column — portable across H2 (dev) and Postgres
 * (prod) without requiring vendor-specific JSON types, while keeping the nested
 * map shape that partial / greedy patches deep-merge against. Insertion order is
 * preserved by deserialising into {@link LinkedHashMap}.
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    // A single shared, thread-safe mapper. Kept independent of the web ObjectMapper
    // so persistence serialisation is stable regardless of MVC config.
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise map to JSON", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise JSON to map", e);
        }
    }
}
