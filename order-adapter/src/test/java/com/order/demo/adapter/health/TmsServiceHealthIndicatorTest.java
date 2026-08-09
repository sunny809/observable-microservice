package com.order.demo.adapter.health;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TmsServiceHealthIndicatorTest {

    private MockWebServer mockWebServer;
    private WebClient webClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("should report UP when TMS health endpoint responds 200")
    void testHealthUpWhenReachable() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        TmsServiceHealthIndicator indicator = new TmsServiceHealthIndicator(webClient);
        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals("tms-service", health.getDetails().get("service"));
        assertEquals("reachable", health.getDetails().get("status"));
    }

    @Test
    @DisplayName("should report DOWN when TMS health endpoint is unreachable")
    void testHealthDownWhenUnreachable() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        TmsServiceHealthIndicator indicator = new TmsServiceHealthIndicator(webClient);
        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("tms-service", health.getDetails().get("service"));
        assertEquals("unreachable", health.getDetails().get("status"));
        assertNotNull(health.getDetails().get("error"), "down health should carry the error message");
    }
}
