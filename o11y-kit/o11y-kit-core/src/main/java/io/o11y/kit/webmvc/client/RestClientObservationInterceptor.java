package io.o11y.kit.webmvc.client;

import io.o11y.kit.http.HttpMetricRecorder;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * Factory for creating {@link ClientHttpRequestInterceptor} instances suitable for
 * use with Spring's {@code RestClient} (Spring 6.2+).
 *
 * <p>Since Spring 6.2, {@code RestClient.Builder.requestInterceptor()} accepts the
 * same {@link ClientHttpRequestInterceptor} interface that {@code RestTemplate} uses.
 * This means the same interceptor implementation works for both clients:
 * <pre>{@code
 * // With RestClient
 * ClientHttpRequestInterceptor interceptor =
 *     RestClientObservationInterceptor.create(recorder);
 * RestClient client = RestClient.builder()
 *     .requestInterceptor(interceptor)
 *     .build();
 *
 * // Equivalent shortcut using RestTemplateObservationInterceptor directly
 * RestClient client = RestClient.builder()
 *     .requestInterceptor(new RestTemplateObservationInterceptor(recorder))
 *     .build();
 * }</pre>
 *
 * <p>This factory exists for three reasons:
 * <ol>
 *   <li><strong>Discoverability:</strong> Developers looking for RestClient support
 *       will find this class naturally via its name.</li>
 *   <li><strong>Type safety:</strong> The factory method returns
 *       {@code ClientHttpRequestInterceptor}, which is the exact type expected by
 *       {@code RestClient.Builder.requestInterceptor()}, avoiding raw-type warnings.</li>
 *   <li><strong>Future-proofing:</strong> If a future Spring version introduces a
 *       dedicated RestClient interceptor interface, this factory can swap the
 *       implementation without breaking callers.</li>
 * </ol>
 *
 * <p><strong>Error semantics:</strong> {@code RestClient} throws
 * {@code RestClientResponseException} for 4xx/5xx responses. The interceptor
 * handles this the same way as with {@code RestTemplate}: HTTP error responses
 * are recorded via {@link HttpMetricRecorder#recordClientRequest} (with the error
 * status code), while network errors (connection refused, timeout) are recorded
 * via {@link HttpMetricRecorder#recordClientError}.
 *
 * @see RestTemplateObservationInterceptor
 * @see AbstractClientObservation
 * @since 0.2.0-alpha
 */
public final class RestClientObservationInterceptor {

    private RestClientObservationInterceptor() {
        // Factory class — not instantiable
    }

    /**
     * Creates a {@link ClientHttpRequestInterceptor} for use with
     * {@code RestClient} that records metrics without OpenTelemetry tracing.
     *
     * @param recorder the metric recorder; must not be null
     * @return a new interceptor instance
     */
    public static ClientHttpRequestInterceptor create(HttpMetricRecorder recorder) {
        return new RestTemplateObservationInterceptor(recorder);
    }

    /**
     * Creates a {@link ClientHttpRequestInterceptor} for use with
     * {@code RestClient} that records metrics and creates OpenTelemetry spans.
     *
     * @param recorder the metric recorder; must not be null
     * @param tracer   the OpenTelemetry tracer; if null, tracing is skipped
     * @return a new interceptor instance
     */
    public static ClientHttpRequestInterceptor create(HttpMetricRecorder recorder, Tracer tracer) {
        return new RestTemplateObservationInterceptor(recorder, tracer);
    }
}
