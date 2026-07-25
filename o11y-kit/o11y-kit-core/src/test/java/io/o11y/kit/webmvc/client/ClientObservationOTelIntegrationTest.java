package io.o11y.kit.webmvc.client;

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
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying that {@link RestTemplateObservationInterceptor} produces
 * correct OpenTelemetry spans when paired with an in-memory OTel SDK.
 *
 * <p>No external OTel collector is required; spans are collected by an
 * {@link InMemorySpanExporter} wired inline.
 *
 * <p><strong>Architecture note:</strong> Unlike the WebFlux
 * {@code ClientObservationHandler} which operates on {@code Mono<ClientResponse>},
 * this interceptor operates synchronously within the
 * {@code ClientHttpRequestInterceptor.intercept()} call. This means:
 * <ul>
 *   <li>HTTP 200: span has {@code http.status_code=200}, no exception events</li>
 *   <li>HTTP 4xx/5xx: the interceptor sees the {@code ClientHttpResponse} first
 *       (calling {@code end()} which records the status code), then
 *       {@code RestTemplate} throws {@code RestClientResponseException} after
 *       the interceptor returns. The span is already ended with the correct
 *       status code before the exception propagates.</li>
 *   <li>Connection refused: span has exception events (the network error occurs
 *       within the interceptor chain)</li>
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
    private RestTemplate restTemplate;

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

        // Set up RestTemplate with RestTemplateObservationInterceptor (with tracing)
        recorder = mock(HttpMetricRecorder.class);
        RestTemplateObservationInterceptor interceptor =
                new RestTemplateObservationInterceptor(recorder, tracer);

        restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(interceptor));
    }

    @AfterEach
    void tearDown() throws IOException {
        tracerProvider.shutdown();
        openTelemetry.close();
        mockServer.shutdown();
    }

    @Test
    void shouldExportOtelSpanOnSuccess() {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        String url = "http://localhost:" + mockServer.getPort() + "/test";
        restTemplate.getForObject(url, String.class);

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
        verify(recorder).recordClientRequest(eq("GET"), anyString(), eq(200), anyLong());
    }

    @Test
    void shouldExportSpanWithStatusCodeOnHttp500() {
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        String url = "http://localhost:" + mockServer.getPort() + "/error";

        // RestTemplate throws for 5xx, but the interceptor has already recorded
        // the response before the exception propagates
        assertThrows(Exception.class, () ->
                restTemplate.getForObject(url, String.class));

        var spans = spanExporter.getFinishedSpanItems();
        assertFalse(spans.isEmpty(), "Expected at least one exported span");

        var spanData = spans.get(0);
        assertEquals("HTTP GET", spanData.getName());

        // Verify 500 status code is recorded on the span
        var attrs = spanData.getAttributes();
        assertEquals(500, attrs.get(io.opentelemetry.api.common.AttributeKey.longKey("http.status_code")));

        // HTTP 500 produces no exception events because the interceptor sees
        // the ClientHttpResponse successfully before RestTemplate throws
        assertTrue(spanData.getEvents().isEmpty(),
                "HTTP 500 should not produce exception events on the interceptor-level span");

        // Verify metrics were recorded for the HTTP response (not as client error)
        verify(recorder).recordClientRequest(eq("GET"), anyString(), eq(500), anyLong());
    }

    @Test
    void shouldExportSpanWithRecordedExceptionOnConnectionRefused() {
        // Create a RestTemplate pointed at a port with no listener
        RestTemplate failingTemplate = new RestTemplate();
        failingTemplate.setInterceptors(List.of(
                new RestTemplateObservationInterceptor(recorder, tracer)));

        assertThrows(Exception.class, () ->
                failingTemplate.getForObject("http://localhost:1/", String.class));

        var spans = spanExporter.getFinishedSpanItems();
        assertFalse(spans.isEmpty(), "Expected at least one exported span for connection failure");

        var spanData = spans.get(0);
        assertEquals("HTTP GET", spanData.getName());

        // Connection errors occur within the interceptor chain,
        // so error() fires and records the exception on the span
        var events = spanData.getEvents();
        assertFalse(events.isEmpty(), "Expected at least one exception event on the span");

        // Verify metrics were recorded as a client error (not a successful request)
        verify(recorder).recordClientError(eq("GET"), anyString(), anyString(), anyLong());
        verify(recorder, never()).recordClientRequest(anyString(), anyString(), anyInt(), anyLong());
    }
}
