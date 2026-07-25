package io.o11y.kit.webmvc.client;

import io.o11y.kit.http.HttpMetricRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link RestTemplateObservationInterceptor}.
 *
 * <p>Uses a combination of {@link MockRestServiceServer} (for HTTP response tests)
 * and direct interceptor invocation (for network error and edge-case tests).
 *
 * <p>Key behavior: {@code MockRestServiceServer} operates at the
 * {@code ClientHttpRequestFactory} level, so the interceptor sees the real
 * {@link ClientHttpResponse} before {@code RestTemplate} converts 4xx/5xx
 * responses into {@code RestClientResponseException}. This means the
 * interceptor's {@code end()} path is exercised for all HTTP status codes.
 *
 * @since 0.2.0-alpha
 */
class RestTemplateObservationInterceptorTest {

    private static final String BASE_URL = "http://localhost:9999";

    private HttpMetricRecorder recorder;
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        recorder = mock(HttpMetricRecorder.class);
        RestTemplateObservationInterceptor interceptor =
                new RestTemplateObservationInterceptor(recorder);

        restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(interceptor));
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void shouldRecordClientRequestOn200Response() {
        mockServer.expect(requestTo(BASE_URL + "/test"))
                .andRespond(withSuccess());

        restTemplate.getForObject(BASE_URL + "/test", String.class);

        verify(recorder).recordClientRequest(eq("GET"), eq("localhost:9999"), eq(200), anyLong());
        verify(recorder, never()).recordClientError(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRecordClientRequestOn4xxResponse() {
        mockServer.expect(requestTo(BASE_URL + "/not-found"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // RestTemplate throws HttpClientErrorException for 4xx, but the interceptor
        // has already seen the ClientHttpResponse and called end() before the exception
        assertThrows(Exception.class, () ->
                restTemplate.getForObject(BASE_URL + "/not-found", String.class));

        // 4xx is an HTTP response, not a network error — must use recordClientRequest
        verify(recorder).recordClientRequest(eq("GET"), eq("localhost:9999"), eq(404), anyLong());
        verify(recorder, never()).recordClientError(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRecordClientRequestOn5xxResponse() {
        mockServer.expect(requestTo(BASE_URL + "/server-error"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // RestTemplate throws HttpServerErrorException for 5xx, but the interceptor
        // has already seen the ClientHttpResponse and called end() before the exception
        assertThrows(Exception.class, () ->
                restTemplate.getForObject(BASE_URL + "/server-error", String.class));

        // 5xx is an HTTP response, not a network error — must use recordClientRequest
        verify(recorder).recordClientRequest(eq("GET"), eq("localhost:9999"), eq(500), anyLong());
        verify(recorder, never()).recordClientError(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRecordClientErrorOnIOException() throws Exception {
        // Directly invoke the interceptor with a mock execution that throws IOException
        RestTemplateObservationInterceptor interceptor =
                new RestTemplateObservationInterceptor(recorder);

        ClientHttpRequest request = mock(ClientHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(new URI("http://inventory-service:8081/api/items"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenThrow(new java.net.ConnectException("Connection refused"));

        assertThrows(IOException.class, () ->
                interceptor.intercept(request, new byte[0], execution));

        verify(recorder).recordClientError(eq("GET"), eq("inventory-service:8081"),
                eq("ConnectException"), anyLong());
        verify(recorder, never()).recordClientRequest(anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    void shouldResolveHostWithoutPortWhenDefaultPort() throws Exception {
        // Directly invoke the interceptor with a URI that has no explicit port
        RestTemplateObservationInterceptor interceptor =
                new RestTemplateObservationInterceptor(recorder);

        ClientHttpRequest request = mock(ClientHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(new URI("http://wms-service/api/shipments"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(request, new byte[0], execution);

        // URI has no explicit port → getPort() returns -1 → host should be just "wms-service"
        verify(recorder).recordClientRequest(eq("POST"), eq("wms-service"), eq(200), anyLong());
    }

    @Test
    void shouldResolveHostWithExplicitPort() throws Exception {
        // Directly invoke the interceptor with a URI that has an explicit port
        RestTemplateObservationInterceptor interceptor =
                new RestTemplateObservationInterceptor(recorder);

        ClientHttpRequest request = mock(ClientHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(new URI("http://inventory-service:8081/api/items"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        when(execution.execute(any(), any())).thenReturn(response);

        interceptor.intercept(request, new byte[0], execution);

        verify(recorder).recordClientRequest(eq("GET"), eq("inventory-service:8081"), eq(200), anyLong());
    }

    @Test
    void shouldHandleRestClientResponseExceptionViaAdapter() throws Exception {
        // Test the RestClientException catch path by simulating an execution that
        // throws RestClientResponseException (which wraps a 4xx response).
        // This exercises the extractResponse() → RestClientResponseAdapter path.
        RestTemplateObservationInterceptor interceptor =
                new RestTemplateObservationInterceptor(recorder);

        ClientHttpRequest request = mock(ClientHttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(new URI("http://api-service:9090/resource"));
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);

        // Simulate RestTemplate throwing HttpClientErrorException (a RestClientResponseException)
        org.springframework.web.client.HttpClientErrorException notFound =
                org.springframework.web.client.HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, new byte[0], null);
        when(execution.execute(any(), any())).thenThrow(notFound);

        assertThrows(org.springframework.web.client.HttpClientErrorException.class, () ->
                interceptor.intercept(request, new byte[0], execution));

        // Should extract the response from the exception and call end(), not error()
        verify(recorder).recordClientRequest(eq("GET"), eq("api-service:9090"), eq(404), anyLong());
        verify(recorder, never()).recordClientError(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRejectNullRecorder() {
        assertThrows(IllegalArgumentException.class,
                () -> new RestTemplateObservationInterceptor(null));
    }

    @Test
    void shouldRejectNullRecorderWithTracer() {
        assertThrows(IllegalArgumentException.class,
                () -> new RestTemplateObservationInterceptor(null, null));
    }
}
