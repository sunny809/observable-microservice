package com.order.demo.adapter.inbound.rest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.port.in.CancelOrderUseCase;
import com.order.demo.application.port.in.OrderCancelledResult;
import com.order.demo.application.port.in.OrderPlacedResult;
import com.order.demo.application.port.in.OrderSummary;
import com.order.demo.application.port.in.PlaceOrderUseCase;
import com.order.demo.application.port.out.OrderQueryPort;
import com.order.demo.adapter.inbound.rest.TraceFilter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Tag("unit")
@Tag("rest-api")
class OrderControllerTest {

    private PlaceOrderUseCase useCase;
    private CancelOrderUseCase cancelOrderUseCase;
    private OrderQueryPort orderQueryPort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(PlaceOrderUseCase.class);
        cancelOrderUseCase = mock(CancelOrderUseCase.class);
        orderQueryPort = mock(OrderQueryPort.class);
        when(useCase.placeOrder(any()))
                .thenReturn(new OrderPlacedResult("ord-123", OrderStatus.CREATED, "mock-trace"));
        OrderController controller = new OrderController(useCase, cancelOrderUseCase, orderQueryPort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(new TraceFilter())
                .build();
    }

    private String orderJson(String customerId, String idempotencyKey, String itemsJson) {
        return """
                {
                    "customerId": "%s",
                    "idempotencyKey": "%s",
                    "items": [%s]
                }
                """.formatted(customerId, idempotencyKey, itemsJson);
    }

    @Test
    @DisplayName("POST /api/v1/orders with valid request returns 201 CREATED with order ID and status")
    void testPlaceOrderSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-1", "idem-1", """
                                {"sku": "SKU-1", "quantity": 5}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ord-123"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("POST /api/v1/orders propagates incoming X-B3-TraceId header")
    void testPlaceOrderUsesIncomingTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-B3-TraceId", "my-trace-abc")
                        .content(orderJson("cust-3", "idem-3", """
                                {"sku": "SKU-3", "quantity": 1}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.traceId").value("my-trace-abc"));
    }

    @Test
    @DisplayName("POST /api/v1/orders rejects empty customer ID with 400 Bad Request")
    void testPlaceOrderRejectsMissingCustomerId() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("", "idem-1", """
                                {"sku": "SKU-1", "quantity": 5}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/orders rejects empty items list with 400 Bad Request")
    void testPlaceOrderRejectsMissingItems() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-1", "idem-1", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/orders with multiple items returns 201 CREATED")
    void testPlaceOrderWithMultipleItems() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-4", "idem-4", """
                                {"sku": "SKU-1", "quantity": 2},
                                {"sku": "SKU-2", "quantity": 3},
                                {"sku": "SKU-3", "quantity": 1}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ord-123"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/by-sku returns orders matching the SKU")
    void testFindBySkuReturnsMatchingOrders() throws Exception {
        OrderSummary summary = new OrderSummary("ord-1", "cust-1", "CREATED",
                Instant.now(), 1, 5, null, null);
        when(orderQueryPort.findBySku("SKU-1")).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/orders/by-sku")
                        .param("sku", "SKU-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value("ord-1"))
                .andExpect(jsonPath("$[0].status").value("CREATED"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/at returns order state at time when found")
    void testGetOrderAtWhenFound() throws Exception {
        Instant pointInTime = Instant.parse("2026-08-01T16:00:00Z");
        OrderSummary summary = new OrderSummary("ord-1", "cust-1", "WMS_ACKED",
                Instant.parse("2026-08-01T15:00:00Z"), 1, 5, null, "WMS_ACKED");
        when(orderQueryPort.findOrderAt("ord-1", pointInTime)).thenReturn(Optional.of(summary));

        mockMvc.perform(get("/api/v1/orders/ord-1/at")
                        .param("time", "2026-08-01T16:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord-1"))
                .andExpect(jsonPath("$.status").value("WMS_ACKED"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}/at returns 404 when no snapshot found")
    void testGetOrderAtWhenNotFound() throws Exception {
        Instant pointInTime = Instant.parse("2026-08-01T16:00:00Z");
        when(orderQueryPort.findOrderAt("ord-1", pointInTime)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/orders/ord-1/at")
                        .param("time", "2026-08-01T16:00:00Z"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/orders propagates exception from use case")
    void testPlaceOrderPropagatesExceptionFromUseCase() {
        when(useCase.placeOrder(any()))
                .thenThrow(new RuntimeException("inventory unavailable"));

        assertThrows(Exception.class, () -> mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-5", "idem-5", """
                                {"sku": "SKU-1", "quantity": 5}
                                """))));
    }

    @Test
    @DisplayName("POST /api/v1/orders rejects blank idempotency key with 400 Bad Request")
    void testPlaceOrderRejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-1", "   ", """
                                {"sku": "SKU-1", "quantity": 5}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/orders/{orderId}/cancel returns 200 with CANCELLED status")
    void testCancelOrderSuccess() throws Exception {
        when(cancelOrderUseCase.cancel(any()))
                .thenReturn(new OrderCancelledResult("ord-123", OrderStatus.CANCELLED, "cancelled"));

        mockMvc.perform(post("/api/v1/orders/ord-123/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"changed mind\",\"customerId\":\"cust-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord-123"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{orderId}/cancel rejects missing reason with 400")
    void testCancelOrderRejectsMissingReason() throws Exception {
        mockMvc.perform(post("/api/v1/orders/ord-123/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"cust-1\"}"))
                .andExpect(status().isBadRequest());
    }
}
