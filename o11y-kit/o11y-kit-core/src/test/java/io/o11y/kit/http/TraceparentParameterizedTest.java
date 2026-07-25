package io.o11y.kit.http;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameterized tests for W3C {@code traceparent} header parsing in
 * {@link TraceIdResolver}.
 *
 * <p>Covers both valid and invalid traceparent formats per the
 * <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
 * specification, as well as edge cases in the current implementation.
 *
 * @since 0.2.0-alpha
 */
class TraceparentParameterizedTest {

    private static final Pattern HEX_32 = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /**
     * Valid W3C traceparent: 00-32hex-16hex-01 → returns the 32-hex trace ID.
     */
    @ParameterizedTest(name = "valid traceparent [{0}]")
    @CsvSource({
            "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01, 0af7651916cd43dd8448eb211c80319c",
            "00-abcdef0123456789abcdef0123456789-abcdef0123456789-01, abcdef0123456789abcdef0123456789"
    })
    void shouldExtractTraceIdFromValidTraceparent(String traceparent, String expectedTraceId) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        assertEquals(expectedTraceId, result);
        assertTrue(HEX_32.matcher(result).matches(),
                "Trace ID must be a valid 32-character hex string");
    }

    /**
     * Invalid: only 3 parts (missing flags) → UUID fallback.
     */
    @ParameterizedTest
    @ValueSource(strings = {"00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331"})
    void shouldFallbackToUuidWhenMissingFlags(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        // Must fall back to UUID since there are only 3 parts
        assertIsUuid(result);
    }

    /**
     * Invalid: version not "00" (e.g., "ff") → UUID fallback.
     * Current implementation does not validate the version field, so it may
     * still extract the trace ID. This test documents the current behavior.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ff-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"})
    void shouldHandleNonZeroVersion(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        // Current implementation does NOT validate version == "00",
        // so it extracts the trace ID even for non-"00" versions.
        // This test documents that behavior.
        assertEquals("0af7651916cd43dd8448eb211c80319c", result);
    }

    /**
     * Invalid: trace-id not 32 hex chars (too short) → UUID fallback.
     */
    @ParameterizedTest
    @ValueSource(strings = {"00-short-b7ad6b7169203331-01"})
    void shouldFallbackToUuidWhenTraceIdTooShort(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        assertIsUuid(result);
    }

    /**
     * Invalid: trace-id has non-hex chars → UUID fallback.
     */
    @ParameterizedTest
    @ValueSource(strings = {"00-0af7651916cd43dd8448eb211c8031ZZ-b7ad6b7169203331-01"})
    void shouldFallbackToUuidWhenTraceIdHasNonHexChars(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        assertIsUuid(result);
    }

    /**
     * Invalid: all-zero trace-id → UUID fallback.
     *
     * <p>Per W3C spec, an all-zero trace-id is invalid. The current
     * implementation does NOT enforce this rule (the regex matches
     * all zeros). This test documents the current behavior: the
     * all-zero trace-id passes validation.
     */
    @ParameterizedTest
    @ValueSource(strings = {"00-00000000000000000000000000000000-b7ad6b7169203331-01"})
    void shouldHandleAllZeroTraceId(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        // Current implementation does NOT reject all-zero trace-ids.
        // It returns "00000000000000000000000000000000" as the trace ID.
        assertEquals("00000000000000000000000000000000", result);
    }

    /**
     * Spaces around parts: the current implementation does NOT trim whitespace.
     *
     * <p>With leading/trailing spaces, the split still produces 4 parts and
     * the trace-id portion (part[1]) remains valid 32-hex, so the resolver
     * extracts it successfully. This test documents that behavior.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            " 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
            "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01 "
    })
    void shouldExtractTraceIdDespiteSpacesAroundParts(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        // The trace-id portion is still valid 32-hex after split,
        // so the resolver extracts it (does not fall back to UUID).
        assertEquals("0af7651916cd43dd8448eb211c80319c", result);
        assertTrue(HEX_32.matcher(result).matches());
    }

    /**
     * Valid: sampled flag "01" → returns trace ID.
     */
    @ParameterizedTest
    @ValueSource(strings = {"00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"})
    void shouldReturnTraceIdWhenSampledFlag(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        assertEquals("0af7651916cd43dd8448eb211c80319c", result);
        assertTrue(HEX_32.matcher(result).matches());
    }

    /**
     * Valid: unsampled flag "00" → still returns trace ID.
     */
    @ParameterizedTest
    @ValueSource(strings = {"00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-00"})
    void shouldReturnTraceIdWhenUnsampledFlag(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        assertEquals("0af7651916cd43dd8448eb211c80319c", result);
        assertTrue(HEX_32.matcher(result).matches());
    }

    /**
     * Invalid: empty string → UUID fallback.
     */
    @ParameterizedTest
    @ValueSource(strings = {""})
    void shouldFallbackToUuidWhenEmptyString(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        assertIsUuid(result);
    }

    /**
     * Invalid: null header → UUID fallback.
     */
    @ParameterizedTest
    @NullSource
    void shouldFallbackToUuidWhenNullHeader(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        assertIsUuid(result);
    }

    /**
     * Valid: uppercase hex in trace-id → returns the trace ID as-is
     * (preserving case per current implementation).
     */
    @ParameterizedTest
    @ValueSource(strings = {"00-0AF7651916CD43DD8448EB211C80319C-b7ad6b7169203331-01"})
    void shouldHandleUppercaseHexInTraceId(String traceparent) {
        String result = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return traceparent;
            return null;
        });
        // The regex [0-9a-fA-F]{32} matches uppercase, and the
        // implementation returns the trace-id as-is (preserving case)
        assertEquals("0AF7651916CD43DD8448EB211C80319C", result);
        assertTrue(HEX_32.matcher(result).matches());
    }

    // --- Helper ---

    private static void assertIsUuid(String value) {
        assertNotNull(value, "Fallback value must not be null");
        assertFalse(value.isBlank(), "Fallback value must not be blank");
        assertTrue(UUID_PATTERN.matcher(value).matches(),
                "Expected a valid UUID fallback, got: " + value);
    }
}
