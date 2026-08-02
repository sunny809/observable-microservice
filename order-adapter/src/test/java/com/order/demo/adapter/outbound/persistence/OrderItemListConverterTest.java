package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.port.in.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OrderItemListConverterTest {

    private OrderItemListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new OrderItemListConverter();
    }

    @Test
    @DisplayName("convertToDatabaseColumn serializes items to JSON")
    void toDatabaseColumn_serializesItems() {
        List<OrderItem> items = List.of(new OrderItem("SKU-1", 3), new OrderItem("SKU-2", 1));
        String json = converter.convertToDatabaseColumn(items);
        assertNotNull(json);
        assertTrue(json.contains("SKU-1"));
        assertTrue(json.contains("SKU-2"));
    }

    @Test
    @DisplayName("convertToDatabaseColumn returns [] for null")
    void toDatabaseColumn_null_returnsEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("convertToDatabaseColumn returns [] for empty list")
    void toDatabaseColumn_emptyList_returnsEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(Collections.emptyList()));
    }

    @Test
    @DisplayName("convertToEntityAttribute deserializes JSON to items")
    void toEntityAttribute_deserializesJson() {
        String json = "[{\"sku\":\"SKU-1\",\"quantity\":3}]";
        List<OrderItem> items = converter.convertToEntityAttribute(json);
        assertEquals(1, items.size());
        assertEquals("SKU-1", items.get(0).getSku());
        assertEquals(3, items.get(0).getQuantity());
    }

    @Test
    @DisplayName("convertToEntityAttribute returns empty list for null")
    void toEntityAttribute_null_returnsEmptyList() {
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("convertToEntityAttribute returns empty list for blank string")
    void toEntityAttribute_blank_returnsEmptyList() {
        assertEquals(Collections.emptyList(), converter.convertToEntityAttribute("   "));
    }

    @Test
    @DisplayName("round-trip: serialize then deserialize preserves data")
    void roundTrip_preservesData() {
        List<OrderItem> original = List.of(new OrderItem("SKU-A", 10));
        String json = converter.convertToDatabaseColumn(original);
        List<OrderItem> restored = converter.convertToEntityAttribute(json);
        assertEquals(original.size(), restored.size());
        assertEquals(original.get(0).getSku(), restored.get(0).getSku());
        assertEquals(original.get(0).getQuantity(), restored.get(0).getQuantity());
    }
}
