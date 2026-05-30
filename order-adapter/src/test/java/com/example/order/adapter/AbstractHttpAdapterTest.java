package com.example.order.adapter;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;

/**
 * Base class for HTTP adapter tests that use MockWebServer.
 * Eliminates duplicated MockWebServer lifecycle management across adapter tests.
 */
@Tag("integration")
@Tag("mock-web-server")
public abstract class AbstractHttpAdapterTest {

    protected MockWebServer mockWebServer;

    @BeforeEach
    protected void setUpMockServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    protected void tearDownMockServer() throws IOException {
        mockWebServer.shutdown();
    }

    protected String baseUrl() {
        return mockWebServer.url("/").toString();
    }
}
