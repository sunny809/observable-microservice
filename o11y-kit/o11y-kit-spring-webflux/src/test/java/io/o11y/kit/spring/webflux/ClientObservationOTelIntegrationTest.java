package io.o11y.kit.spring.webflux;

import io.o11y.kit.http.HttpMetricRecorder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying that {@link ClientObservationHandler} produces
 * correct OpenTelemetry spans when paired with an in-memory OTel SDK.
 *
 * <p>No external OTel collector is required; spans are collected by an
 * {@link InMemorySpanExporter} wired inline.
 *
 * <p><strong>Architecture note:</strong> The {@code ClientObservationHandler} is an
 * {@code ExchangeFilterFunction} that operates on the {@code Mono<ClientResponse>} level.
 * For HTTP 4xx/5xx responses, the {@code ClientResponse} is emitted successfully by the
 * filter chain, so {@code doOnNext} fires (recording metrics and span attributes) and
 * {@code doFinally} ends the span. The {@code WebClientResponseException} is thrown
 * downstream by {@code retrieve()}, outside the filter chain, so it does NOT trigger
 * {@code doOnError} handlers in the filter. This means:
 * <ul>
 *   <li>HTTP 500: span has {@code http.status_code=500} but no exception events</li>
 *   <li>Connection refused: span has exception events (the network error occurs
 *       within the filter chain)</li>
 * </ul>
 *
 * @since 0.2.0-alpha
 */
class ClientObservationOTelIntegrationTest {

    private MockWebServer mockServer;
    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private OpenTelemetrySdk openTelemetry;
    private Tracer tracer;
    private HttpMetricRecorder recorder;
    private WebClient webClient;

    @BeforeEach
    void setUp() throws IOException {
        // Set up in-memory OTel pipeline
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        tracer = openTelemetry.getTracer("test-tracer");

        // Set up mock HTTP server
        mockServer = new MockWebServer();
        mockServer.start();

        // Set up WebClient with ClientObservationHandler (with tracing)
        recorder = mock(HttpMetricRecorder.class);
        ClientObservationHandler handler = new ClientObservationHandler(recorder, tracer);

        webClient = WebClient.builder()
                .baseUrl("http://localhost:" + mockServer.getPort())
                .filter(handler)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        tracerProvider.shutdown();
        openTelemetry.close();
        mockServer.shutdown();
    }

    @Test
    void shouldExportOtelSpanOnSuccess() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        webClient.get()
                .uri("/test")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();

        // Allow async span completion to propagate
        Thread.sleep(200);

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size(), "Expected exactly one exported span");

        var spanData = spans.get(0);
        assertEquals("HTTP GET", spanData.getName());

        // Verify span attributes
        var attrs = spanData.getAttributes();
        assertEquals("GET", attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("http.method")));
        assertEquals(200, attrs.get(io.opentelemetry.api.common.AttributeKey.longKey("http.status_code")));
        assertNotNull(attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("http.host")));

        // Verify trace ID is non-empty
        assertFalse(spanData.getTraceId().isEmpty(), "Trace ID must not be empty");

        // Verify metrics were also recorded
        verify(recorder, timeout(2000))
                .recordClientRequest(eq("GET"), contains("localhost"), eq(200), anyLong());
    }

    @Test
    void shouldExportSpanWithStatusCodeOnHttp500() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        webClient.get()
                .uri("/error")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectError()
                .verify();

        // Allow async span completion to propagate
        Thread.sleep(200);

        var spans = spanExporter.getFinishedSpanItems();
        assertFalse(spans.isEmpty(), "Expected at least one exported span");

        var spanData = spans.get(0);
        assertEquals("HTTP GET", spanData.getName());

        // Verify 500 status code is recorded on the span
        var attrs = spanData.getAttributes();
        assertEquals(500, attrs.get(io.opentelemetry.api.common.AttributeKey.longKey("http.status_code")));

        // The ClientResponse is emitted successfully at the filter level,
        // so doOnNext fires and the span ends via doFinally.
        // The WebClientResponseException is thrown downstream by retrieve(),
        // outside the filter chain — no exception events appear on the span.
        assertTrue(spanData.getEvents().isEmpty(),
                "HTTP 500 should not produce exception events on the filter-level span");

        // Verify metrics were recorded for the HTTP response (not as client error)
        verify(recorder, timeout(2000))
                .recordClientRequest(eq("GET"), contains("localhost"), eq(500), anyLong());
    }

    @Test
    void shouldExportSpanWithRecordedExceptionOnConnectionRefused() throws InterruptedException {
        // Use a WebClient pointed at a port with no listener
        WebClient failingClient = WebClient.builder()
                .baseUrl("http://localhost:1")
                .filter(new ClientObservationHandler(recorder, tracer))
                .build();

        failingClient.get()
                .uri("/")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectError()
                .verify();

        // Allow async span completion to propagate
        Thread.sleep(200);

        var spans = spanExporter.getFinishedSpanItems();
        assertFalse(spans.isEmpty(), "Expected at least one exported span for connection failure");

        var spanData = spans.get(0);
        assertEquals("HTTP GET", spanData.getName());

        // Connection errors occur within the filter chain,
        // so doOnError fires and records the exception on the span.
        var events = spanData.getEvents();
        assertFalse(events.isEmpty(), "Expected at least one exception event on the span");

        // Verify metrics were recorded as a client error (not a successful request)
        verify(recorder, timeout(2000))
                .recordClientError(eq("GET"), contains("localhost"), anyString(), anyLong());
        verify(recorder, never())
                .recordClientRequest(anyString(), anyString(), anyInt(), anyLong());
    }
}
