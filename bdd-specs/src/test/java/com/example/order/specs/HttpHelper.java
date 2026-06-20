package com.example.order.specs;

import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class HttpHelper {

    private final TestRestTemplate restTemplate;

    public HttpHelper(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<Map> postOrder(String customerId, String sku, int quantity, String idempotencyKey) {
        String itemsJson = String.format("[{\"sku\":\"%s\",\"quantity\":%d}]", sku, quantity);
        return postOrder(customerId, idempotencyKey, itemsJson, defaultHeaders());
    }

    public ResponseEntity<Map> postOrder(String customerId, String sku, int quantity, String idempotencyKey, HttpHeaders headers) {
        String itemsJson = String.format("[{\"sku\":\"%s\",\"quantity\":%d}]", sku, quantity);
        return postOrder(customerId, idempotencyKey, itemsJson, headers);
    }

    public ResponseEntity<Map> postOrder(String customerId, String idempotencyKey, String itemsJson, HttpHeaders headers) {
        String body = String.format("{\"customerId\":\"%s\",\"idempotencyKey\":\"%s\",\"items\":%s}",
                customerId, idempotencyKey, itemsJson);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity("/api/v1/orders", request, Map.class);
    }

    public ResponseEntity<Map> postOrderRaw(String rawBody, HttpHeaders headers) {
        HttpEntity<String> request = new HttpEntity<>(rawBody, headers);
        return restTemplate.postForEntity("/api/v1/orders", request, Map.class);
    }

    public HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Trace-Id", "trace-" + java.util.UUID.randomUUID());
        return headers;
    }

    public HttpHeaders headersWithXB3TraceId(String traceId) {
        HttpHeaders headers = defaultHeaders();
        headers.set("X-B3-TraceId", traceId);
        return headers;
    }

    public HttpHeaders headersWithTraceparent(String traceparent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("traceparent", traceparent);
        return headers;
    }
}