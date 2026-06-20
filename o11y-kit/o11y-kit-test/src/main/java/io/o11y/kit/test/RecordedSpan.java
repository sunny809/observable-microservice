package io.o11y.kit.test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single recorded OpenTelemetry span, captured by the
 * {@link OtelTestHarness} in-memory exporter.
 *
 * <p>Provides access to the span's trace ID, span ID, name, attributes,
 * and status for test assertions.
 *
 * @since 0.2.0-alpha
 */
public final class RecordedSpan {

    private final String traceId;
    private final String spanId;
    private final String name;
    private final Map<String, Object> attributes;
    private final Status status;

    private RecordedSpan(String traceId, String spanId, String name,
                         Map<String, Object> attributes, Status status) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.name = name;
        this.attributes = Collections.unmodifiableMap(attributes);
        this.status = status;
    }

    /**
     * Creates a {@code RecordedSpan} from an OTel SDK {@link SpanData} instance.
     *
     * <p>Converts the span data's attributes from the OTel typed attribute map
     * into a plain {@code Map<String, Object>}, and maps the OTel status code
     * to the {@link Status} enum.
     *
     * @param spanData the OTel SDK span data to convert; must not be null
     * @return a new {@code RecordedSpan} representing the same data
     */
    public static RecordedSpan from(SpanData spanData) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        spanData.getAttributes().forEach((key, value) -> attrs.put(key.getKey(), value));

        Status status = switch (spanData.getStatus().getStatusCode()) {
            case OK -> Status.OK;
            case ERROR -> Status.ERROR;
            case UNSET -> Status.UNSET;
        };

        return new RecordedSpan(
                spanData.getTraceId(),
                spanData.getSpanId(),
                spanData.getName(),
                attrs,
                status
        );
    }

    /**
     * Returns the trace ID of this span as a 32-character lowercase hex string.
     *
     * @return the trace ID
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Returns the span ID of this span as a 16-character lowercase hex string.
     *
     * @return the span ID
     */
    public String getSpanId() {
        return spanId;
    }

    /**
     * Returns the name of this span (e.g., "HTTP GET").
     *
     * @return the span name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the attributes recorded on this span as an unmodifiable map.
     *
     * <p>Keys are attribute names (e.g., "http.method", "http.status_code");
     * values are the attribute values in their native Java types
     * (String, Long, Double, Boolean, etc.).
     *
     * @return the span attributes
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Returns the status of this span.
     *
     * @return the span status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * The status of a recorded span, mirroring the OTel {@code StatusCode} enum.
     *
     * @since 0.2.0-alpha
     */
    public enum Status {
        /** The operation completed successfully. */
        OK,
        /** The operation completed with an error. */
        ERROR,
        /** The status is unset (default for spans without explicit status). */
        UNSET
    }
}
