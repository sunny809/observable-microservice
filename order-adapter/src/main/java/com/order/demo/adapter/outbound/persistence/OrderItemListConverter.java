package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.port.in.OrderItem;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA AttributeConverter that serializes/deserializes {@code List<OrderItem>}
 * to/from JSON. Replaces the manual serialization previously in
 * {@link OrderPersistenceAdapter}.
 *
 * <p>Works with both TEXT columns (H2) and JSONB columns (PostgreSQL).
 * The database column type is irrelevant — this converter handles the
 * Java ↔ String mapping; the JDBC driver handles String ↔ column type.
 */
@Converter
public class OrderItemListConverter implements AttributeConverter<List<OrderItem>, String> {

    private static final Logger log = LoggerFactory.getLogger(OrderItemListConverter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // OrderItem has no default constructor or @JsonCreator, so we register
        // a mixin that tells Jackson how to deserialize it via the all-args constructor.
        MAPPER.addMixIn(OrderItem.class, OrderItemMixin.class);
    }

    /** Jackson mixin for {@link OrderItem} deserialization support. */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    abstract static class OrderItemMixin {
        @JsonCreator
        OrderItemMixin(@JsonProperty("sku") String sku,
                       @JsonProperty("quantity") int quantity) {}
    }

    @Override
    public String convertToDatabaseColumn(List<OrderItem> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order items to JSON", e);
        }
    }

    @Override
    public List<OrderItem> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<OrderItem>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize order items from JSON: {}", dbData, e);
            throw new IllegalStateException("Failed to deserialize order items — possible data corruption", e);
        }
    }
}
