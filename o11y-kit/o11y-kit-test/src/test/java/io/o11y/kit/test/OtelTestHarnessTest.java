package io.o11y.kit.test;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link OtelTestHarness}.
 */
class OtelTestHarnessTest {

    @Test
    void createReturnsHarnessWithWorkingTracer() {
        try (OtelTestHarness harness = OtelTestHarness.create()) {
            Tracer tracer = harness.getTracer();
            assertThat(tracer).isNotNull();

            // The tracer should be usable — create a span without error
            var span = tracer.spanBuilder("test-span").startSpan();
            try (Scope ignored = span.makeCurrent()) {
                // span is active
                assertThat(span.isRecording()).isTrue();
            } finally {
                span.end();
            }
        }
    }

    @Test
    void spansRecordedViaTracerAppearInGetSpansAfterFlush() {
        try (OtelTestHarness harness = OtelTestHarness.create()) {
            Tracer tracer = harness.getTracer();

            // Create and end a span
            var span = tracer.spanBuilder("my-test-span").startSpan();
            span.setAttribute("test.key", "test-value");
            span.end();

            // Flush to ensure spans are exported
            harness.flush();

            // Verify the span appears in getSpans()
            var spans = harness.getSpans();
            assertThat(spans).hasSize(1);

            RecordedSpan recorded = spans.get(0);
            assertThat(recorded.getName()).isEqualTo("my-test-span");
            assertThat(recorded.getTraceId()).isNotBlank();
            assertThat(recorded.getSpanId()).isNotBlank();
            assertThat(recorded.getStatus()).isEqualTo(RecordedSpan.Status.UNSET);
            assertThat(recorded.getAttributes())
                    .containsEntry("test.key", "test-value");
        }
    }

    @Test
    void multipleSpansAreAllCollected() {
        try (OtelTestHarness harness = OtelTestHarness.create()) {
            Tracer tracer = harness.getTracer();

            tracer.spanBuilder("span-1").startSpan().end();
            tracer.spanBuilder("span-2").startSpan().end();
            tracer.spanBuilder("span-3").startSpan().end();

            harness.flush();

            assertThat(harness.getSpans()).hasSize(3);
            assertThat(harness.getSpans().stream().map(RecordedSpan::getName))
                    .containsExactly("span-1", "span-2", "span-3");
        }
    }

    @Test
    void closeShutsDownCleanlyWithoutThrowing() {
        OtelTestHarness harness = OtelTestHarness.create();
        assertThatNoException().isThrownBy(harness::close);
    }

    @Test
    void closeIsIdempotent() {
        OtelTestHarness harness = OtelTestHarness.create();
        assertThatNoException().isThrownBy(() -> {
            harness.close();
            harness.close(); // second call should be a no-op
        });
    }

    @Test
    void spanWithOkStatusIsRecordedCorrectly() {
        try (OtelTestHarness harness = OtelTestHarness.create()) {
            Tracer tracer = harness.getTracer();

            var span = tracer.spanBuilder("ok-span").startSpan();
            span.setAttribute("http.status_code", 200L);
            span.end();

            harness.flush();

            assertThat(harness.getSpans()).hasSize(1);
            assertThat(harness.getSpans().get(0).getAttributes())
                    .containsEntry("http.status_code", 200L);
        }
    }
}
