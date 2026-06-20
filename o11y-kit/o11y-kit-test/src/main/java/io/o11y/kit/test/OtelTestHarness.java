package io.o11y.kit.test;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.util.List;

/**
 * Test harness that provides a fully-wired OpenTelemetry SDK for integration tests.
 *
 * <p>Creates an in-memory OTel pipeline with a {@link InMemorySpanExporter}
 * so that tests can assert on exported spans without requiring an external
 * OTel collector.
 *
 * <p>Usage:
 * <pre>{@code
 * try (OtelTestHarness harness = OtelTestHarness.create()) {
 *     // ... exercise the system under test ...
 *     harness.flush();
 *     InMemorySpanExporter exporter = harness.getSpanExporter();
 *     // assert on exported spans
 * }
 * }</pre>
 *
 * @since 0.2.0-alpha
 */
public class OtelTestHarness implements AutoCloseable {

    private static final String TRACER_NAME = "o11y-kit-test";

    private final InMemorySpanExporter spanExporter;
    private final SdkTracerProvider tracerProvider;
    private final OpenTelemetrySdk openTelemetry;
    private final Tracer tracer;
    private volatile boolean closed;

    private OtelTestHarness(InMemorySpanExporter spanExporter,
                            SdkTracerProvider tracerProvider,
                            OpenTelemetrySdk openTelemetry) {
        this.spanExporter = spanExporter;
        this.tracerProvider = tracerProvider;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer(TRACER_NAME);
    }

    /**
     * Creates a new {@code OtelTestHarness} with a default in-memory OTel pipeline.
     *
     * <p>The harness is ready to use immediately after creation. Spans are
     * collected by an {@link InMemorySpanExporter} and can be retrieved
     * via {@link #getSpanExporter()}.
     *
     * @return a new test harness instance
     */
    public static OtelTestHarness create() {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        return new OtelTestHarness(spanExporter, tracerProvider, openTelemetry);
    }

    /**
     * Returns the in-memory span exporter that collects all exported spans.
     *
     * <p>Call {@link InMemorySpanExporter#getFinishedSpanItems()} to retrieve
     * the list of completed spans for assertion.
     *
     * @return the in-memory span exporter
     */
    public InMemorySpanExporter getSpanExporter() {
        return spanExporter;
    }

    /**
     * Returns a tracer obtained from the in-memory OTel SDK.
     *
     * <p>Use this tracer in tests to create spans that will be collected
     * by the in-memory exporter.
     *
     * @return a tracer for creating test spans
     */
    public Tracer getTracer() {
        return tracer;
    }

    /**
     * Flushes any pending spans to the in-memory exporter.
     *
     * <p>Call this before asserting on exported spans to ensure that
     * all completed spans have been collected.
     */
    public void flush() {
        tracerProvider.forceFlush().join(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Returns all exported spans as a list of {@link RecordedSpan} instances.
     *
     * <p>For the most up-to-date results, call {@link #flush()} before
     * invoking this method.
     *
     * @return the list of recorded spans
     */
    public List<RecordedSpan> getSpans() {
        return spanExporter.getFinishedSpanItems().stream()
                .map(RecordedSpan::from)
                .toList();
    }

    /**
     * Closes the harness and releases all OTel SDK resources.
     *
     * <p>After closing, the harness must not be used. Any spans that
     * have not been flushed will be lost.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // openTelemetry.close() internally shuts down the tracer provider,
        // so we do not call tracerProvider.shutdown() separately to avoid
        // the "Calling shutdown() multiple times" warning.
        openTelemetry.close();
    }
}
