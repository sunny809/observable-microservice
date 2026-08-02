package com.order.demo.adapter.outbound.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class StringListConverterTest {

    private StringListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringListConverter();
    }

    @Test
    @DisplayName("convertToDatabaseColumn serializes list to JSON")
    void toDatabaseColumn_serializesList() {
        List<String> ids = List.of("res-1", "res-2");
        String json = converter.convertToDatabaseColumn(ids);
        assertEquals("[\"res-1\",\"res-2\"]", json);
    }

    @Test
    @DisplayName("convertToDatabaseColumn returns [] for null")
    void toDatabaseColumn_null_returnsEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("convertToEntityAttribute deserializes JSON to list")
    void toEntityAttribute_deserializesJson() {
        String json = "[\"res-1\",\"res-2\"]";
        List<String> ids = converter.convertToEntityAttribute(json);
        assertEquals(List.of("res-1", "res-2"), ids);
    }

    @Test
    @DisplayName("convertToEntityAttribute returns empty list for null")
    void toEntityAttribute_null_returnsEmptyList() {
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("round-trip preserves data")
    void roundTrip_preservesData() {
        List<String> original = List.of("abc", "def");
        String json = converter.convertToDatabaseColumn(original);
        List<String> restored = converter.convertToEntityAttribute(json);
        assertEquals(original, restored);
    }
}
