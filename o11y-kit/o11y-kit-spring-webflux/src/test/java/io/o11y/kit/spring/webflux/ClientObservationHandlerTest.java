package io.o11y.kit.spring.webflux;

import io.o11y.kit.http.HttpMetricRecorder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ClientObservationHandler}.
 */
class ClientObservationHandlerTest {

    private MockWebServer mockServer;
    private HttpMetricRecorder recorder;
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        mockServer = new MockWebServer();
        recorder = mock(HttpMetricRecorder.class);
        var handler = new ClientObservationHandler(recorder);

        webClient = WebClient.builder()
                .baseUrl("http://localhost:" + mockServer.getPort())
                .filter(handler)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void shouldRecordClientRequestOnSuccess() {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        webClient.get()
                .uri("/test")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();

        verify(recorder, timeout(2000)).recordClientRequest(eq("GET"), contains("localhost"), eq(200), anyLong());
        verify(recorder, never()).recordClientError(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldNotDoubleCountHttpError() {
        // 4xx/5xx: recordClientRequest fires, recordClientError must NOT fire for HTTP errors
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        webClient.get()
                .uri("/error")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectError()
                .verify();

        verify(recorder, timeout(2000)).recordClientRequest(eq("GET"), contains("localhost"), eq(500), anyLong());
        verify(recorder, never()).recordClientError(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRecordClientErrorOnConnectionFailure() {
        // Connect to a port that nothing is listening on → network error, not HTTP
        WebClient failingClient = WebClient.builder()
                .baseUrl("http://localhost:1")
                .filter(new ClientObservationHandler(recorder))
                .build();

        failingClient.get()
                .uri("/")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectError()
                .verify();

        verify(recorder, timeout(2000)).recordClientError(eq("GET"), contains("localhost"), anyString(), anyLong());
        verify(recorder, never()).recordClientRequest(anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    void shouldUseHostWithoutPortWhenDefaultPort() {
        mockServer.enqueue(new MockResponse().setResponseCode(200));

        webClient.get()
                .uri("/no-port")
                .retrieve()
                .toBodilessEntity()
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();

        // host should NOT contain ":-1"
        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        verify(recorder, timeout(2000))
                .recordClientRequest(eq("GET"), hostCaptor.capture(), eq(200), anyLong());
        assertFalse(hostCaptor.getValue().contains(":-1"),
                "Host tag should not contain :-1 for default-port URLs");
    }
}
