package io.o11y.kit.spring.webmvc.client;

import io.o11y.kit.http.HttpMetricRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RestClientObservationInterceptor}.
 *
 * <p>Validates that the factory creates interceptors that behave identically to
 * {@link RestTemplateObservationInterceptor} when used with {@link RestClient}.
 * Since both clients share the same {@code ClientHttpRequestInterceptor} interface
 * (Spring 6.2+), the core observation logic is exercised through the same
 * {@link AbstractClientObservation} base class.
 *
 * <p>Tests use direct interceptor invocation to verify metric recording, and
 * a {@link RestClient} integration to verify the factory wiring.
 *
 * @since 0.2.0-alpha
 */
class RestClientObservationInterceptorTest {

    private HttpMetricRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = mock(HttpMetricRecorder.class);
    }

    @Test
    void shouldCreateInterceptorWithoutTracer() {
        var interceptor = RestClientObservationInterceptor.create(recorder);
        assertNotNull(interceptor);
    }

    @Test
    void shouldCreateInterceptorWithTracer() {
        var interceptor = RestClientObservationInterceptor.create(recorder, null);
        assertNotNull(interceptor);
    }

    @Test
    void shouldRecordClientRequestOnSuccess() throws Exception {
        var interceptor = RestClientObservationInterceptor.create(recorder);

        ClientHttpRequest request = mock(ClientHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(new URI("http://api-service:8080/data"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(request, new byte[0], execution);

        verify(recorder).recordClientRequest(eq("GET"), eq("api-service:8080"), eq(200), anyLong());
        verify(recorder, never()).recordClientError(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRecordClientRequestOn5xxResponse() throws Exception {
        var interceptor = RestClientObservationInterceptor.create(recorder);

        ClientHttpRequest request = mock(ClientHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(new URI("http://api-service:8080/fail"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = new MockClientHttpResponse(new byte[0],
                HttpStatus.INTERNAL_SERVER_ERROR);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(request, new byte[0], execution);

        verify(recorder).recordClientRequest(eq("GET"), eq("api-service:8080"), eq(500), anyLong());
        verify(recorder, never()).recordClientError(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRecordClientErrorOnIOException() throws Exception {
        var interceptor = RestClientObservationInterceptor.create(recorder);

        ClientHttpRequest request = mock(ClientHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(new URI("http://wms-service:9090/ship"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenThrow(
                new java.net.ConnectException("Connection refused"));

        assertThrows(IOException.class, () ->
                interceptor.intercept(request, new byte[0], execution));

        verify(recorder).recordClientError(eq("POST"), eq("wms-service:9090"),
                eq("ConnectException"), anyLong());
        verify(recorder, never()).recordClientRequest(anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    void shouldResolveHostWithoutPortForDefaultPort() throws Exception {
        var interceptor = RestClientObservationInterceptor.create(recorder);

        ClientHttpRequest request = mock(ClientHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(new URI("http://api-service/items"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(request, new byte[0], execution);

        // No explicit port → host should be "api-service" (no :-1 suffix)
        verify(recorder).recordClientRequest(eq("GET"), eq("api-service"), eq(200), anyLong());
    }

    @Test
    void shouldWorkWithRestClientBuilder() {
        // Verify the factory method produces an interceptor that RestClient.Builder accepts
        var interceptor = RestClientObservationInterceptor.create(recorder);

        // This should not throw — validates the type is compatible
        assertDoesNotThrow(() ->
                RestClient.builder()
                        .requestInterceptor(interceptor)
                        .build());
    }

    @Test
    void shouldPropagateNullRecorderCheck() {
        // The factory delegates to RestTemplateObservationInterceptor,
        // which rejects null recorder
        assertThrows(IllegalArgumentException.class,
                () -> RestClientObservationInterceptor.create(null));
    }
}
