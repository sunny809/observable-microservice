package io.o11y.kit.spring.webmvc.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Adapts a {@link RestClientResponseException} to the {@link ClientHttpResponse}
 * interface so that {@link AbstractClientObservation#end} can extract the HTTP
 * status code.
 *
 * <p>This is needed because {@code RestTemplate} throws
 * {@code RestClientResponseException} for 4xx/5xx responses rather than
 * returning a {@code ClientHttpResponse} normally. The exception carries the
 * status code and headers, which we wrap to satisfy the
 * {@code ClientHttpResponse} contract.
 *
 * @since 0.2.0-alpha
 */
class RestClientResponseAdapter implements ClientHttpResponse {

    private final RestClientResponseException exception;

    RestClientResponseAdapter(RestClientResponseException exception) {
        this.exception = exception;
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return exception.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return exception.getStatusText();
    }

    @Override
    public InputStream getBody() throws IOException {
        byte[] body = exception.getResponseBodyAsByteArray();
        return (body != null) ? new ByteArrayInputStream(body) : InputStream.nullInputStream();
    }

    @Override
    public HttpHeaders getHeaders() {
        return exception.getResponseHeaders();
    }

    @Override
    public void close() {
        // No-op: the response body is already fully read by the exception
    }
}
