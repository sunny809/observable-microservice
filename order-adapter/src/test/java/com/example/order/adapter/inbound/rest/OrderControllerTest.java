package com.example.order.adapter.inbound.rest;

import com.example.order.application.domain.OrderStatus;
import com.example.order.application.port.in.OrderPlacedResult;
import com.example.order.application.port.in.PlaceOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("unit")
@Tag("rest-api")
class OrderControllerTest {

    private PlaceOrderUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(PlaceOrderUseCase.class);
        when(useCase.placeOrder(any()))
                .thenReturn(new OrderPlacedResult("ord-123", OrderStatus.CREATED, "mock-trace"));
        OrderController controller = new OrderController(useCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new TraceFilter())
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
    @DisplayName("POST /api/orders with valid request returns 201 CREATED with order ID and status")
    void testPlaceOrderSuccess() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-1", "idem-1", """
                                {"sku": "SKU-1", "quantity": 5}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ord-123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("POST /api/orders generates trace ID when X-Trace-Id header is absent")
    void testPlaceOrderGeneratesTraceIdWhenNoneProvided() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-2", "idem-2", """
                                {"sku": "SKU-2", "quantity": 3}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("POST /api/orders propagates incoming X-B3-TraceId header")
    void testPlaceOrderUsesIncomingTraceId() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-B3-TraceId", "my-trace-abc")
                        .content(orderJson("cust-3", "idem-3", """
                                {"sku": "SKU-3", "quantity": 1}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "my-trace-abc"));
    }

    @Test
    @DisplayName("POST /api/orders rejects empty customer ID with 400 Bad Request")
    void testPlaceOrderRejectsMissingCustomerId() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("", "idem-1", """
                                {"sku": "SKU-1", "quantity": 5}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders rejects empty items list with 400 Bad Request")
    void testPlaceOrderRejectsMissingItems() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-1", "idem-1", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders with multiple items returns 201 CREATED")
    void testPlaceOrderWithMultipleItems() throws Exception {
        mockMvc.perform(post("/api/orders")
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
    @DisplayName("POST /api/orders propagates exception from use case")
    void testPlaceOrderPropagatesExceptionFromUseCase() {
        when(useCase.placeOrder(any()))
                .thenThrow(new RuntimeException("inventory unavailable"));

        assertThrows(Exception.class, () -> mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-5", "idem-5", """
                                {"sku": "SKU-1", "quantity": 5}
                                """))));
    }

    @Test
    @DisplayName("POST /api/orders rejects blank idempotency key with 400 Bad Request")
    void testPlaceOrderRejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("cust-1", "   ", """
                                {"sku": "SKU-1", "quantity": 5}
                                """)))
                .andExpect(status().isBadRequest());
    }
}
