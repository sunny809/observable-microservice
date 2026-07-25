package io.o11y.kit.webmvc.client;

import io.o11y.kit.http.HttpMetricRecorder;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;

import java.io.IOException;

/**
 * {@link ClientHttpRequestInterceptor} that records client-side HTTP callout metrics
 * and optional OpenTelemetry spans for Spring's {@code RestTemplate}.
 *
 * <p>For each outbound request, it:
 * <ul>
 *   <li>Records round-trip duration via {@link HttpMetricRecorder#recordClientRequest}
 *       for all HTTP responses (including 4xx and 5xx)</li>
 *   <li>Records network-level errors (connection refused, timeout, DNS failure)
 *       via {@link HttpMetricRecorder#recordClientError}</li>
 *   <li>Optionally creates an OpenTelemetry child span for the callout</li>
 * </ul>
 *
 * <p><strong>HTTP error handling:</strong> When {@code RestTemplate} receives a 4xx/5xx
 * response, it throws {@link RestClientException}. However, this is an HTTP-level error
 * with a valid status code, not a network error. This interceptor extracts the
 * {@link ClientHttpResponse} from the exception and records it via
 * {@code recordClientRequest} (not {@code recordClientError}), matching the same
 * semantics as the WebFlux {@code ClientObservationHandler}.
 *
 * <p><strong>RestClient compatibility:</strong> Since Spring 6.2,
 * {@code RestClient.Builder.requestInterceptor()} accepts the same
 * {@link ClientHttpRequestInterceptor} interface. This interceptor can be used
 * directly with both {@code RestTemplate} and {@code RestClient}:
 * <pre>{@code
 * // With RestTemplate
 * RestTemplate template = new RestTemplate();
 * template.getInterceptors().add(new RestTemplateObservationInterceptor(recorder));
 *
 * // With RestClient (Spring 6.2+)
 * RestClient client = RestClient.builder()
 *     .requestInterceptor(new RestTemplateObservationInterceptor(recorder))
 *     .build();
 * }</pre>
 *
 * <p>For a dedicated factory method, see {@link RestClientObservationInterceptor}.
 *
 * @see AbstractClientObservation
 * @see RestClientObservationInterceptor
 * @since 0.2.0-alpha
 */
public class RestTemplateObservationInterceptor
        extends AbstractClientObservation
        implements ClientHttpRequestInterceptor {

    /**
     * Creates a new {@code RestTemplateObservationInterceptor} without
     * OpenTelemetry tracing.
     *
     * @param recorder the metric recorder; must not be null
     */
    public RestTemplateObservationInterceptor(HttpMetricRecorder recorder) {
        super(recorder);
    }

    /**
     * Creates a new {@code RestTemplateObservationInterceptor} with
     * OpenTelemetry tracing.
     *
     * @param recorder the metric recorder; must not be null
     * @param tracer   the OpenTelemetry tracer; if null, tracing is skipped
     */
    public RestTemplateObservationInterceptor(HttpMetricRecorder recorder, Tracer tracer) {
        super(recorder, tracer);
    }

    /**
     * Intercepts an outbound HTTP request to record metrics and optionally
     * create an OTel span.
     *
     * <p>Lifecycle:
     * <ol>
     *   <li>{@link #begin(HttpRequest)} — record start time, extract method/host,
     *       start OTel span if tracer is configured</li>
     *   <li>Delegate to {@code execution.execute()} for the actual HTTP call</li>
     *   <li>On success: {@link #end(ObservationContext, ClientHttpResponse)}</li>
     *   <li>On {@link RestClientException} (HTTP 4xx/5xx): extract the response
     *       and call {@link #end}; this is an HTTP error, not a network error</li>
     *   <li>On {@link IOException} (network error): call
     *       {@link #error(ObservationContext, Throwable)}</li>
     *   <li>In finally: ensure the OTel span is ended if an unexpected exception
     *       bypasses both {@code end} and {@code error}</li>
     * </ol>
     *
     * @param request   the outbound HTTP request
     * @param body      the request body
     * @param execution the execution chain to delegate to
     * @return the HTTP response
     * @throws IOException if an I/O error occurs during execution
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        ObservationContext ctx = begin(request);
        try {
            ClientHttpResponse response = execution.execute(request, body);
            end(ctx, response);
            return response;
        } catch (RestClientException e) {
            // RestTemplate throws RestClientException for 4xx/5xx, but the
            // HTTP response is still valid. Extract it and record as a
            // successful HTTP exchange (with the error status code).
            ClientHttpResponse response = extractResponse(e);
            if (response != null) {
                end(ctx, response);
            } else {
                error(ctx, e);
            }
            throw e;
        } catch (IOException e) {
            error(ctx, e);
            throw e;
        } catch (RuntimeException e) {
            // Unexpected runtime exception: ensure span is ended
            if (ctx.span() != null) {
                ctx.span().recordException(e);
                ctx.span().end();
            }
            throw e;
        }
    }

    /**
     * Attempts to extract a {@link ClientHttpResponse} from a
     * {@link RestClientException}.
     *
     * <p>{@code RestClientException} itself does not expose the response, but
     * its subclass {@code RestClientResponseException} (which covers all HTTP
     * status errors) does. If the response is available, we can record the
     * status code via {@link #end}; otherwise we fall back to
     * {@link #error(ObservationContext, Throwable)}.
     *
     * @param e the RestClientException
     * @return the ClientHttpResponse if available, null otherwise
     */
    private static ClientHttpResponse extractResponse(RestClientException e) {
        if (e instanceof org.springframework.web.client.RestClientResponseException responseException) {
            // RestClientResponseException wraps the response but doesn't expose
            // the ClientHttpResponse directly. We create a minimal adapter that
            // returns the status code.
            return new RestClientResponseAdapter(responseException);
        }
        return null;
    }
}
