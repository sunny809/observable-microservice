package com.order.demo.specs;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class ObservabilitySteps {

    @Autowired
    private HttpHelper httpHelper;

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<String> prometheusResponse;

    @When("the client places an order with idempotency key {string}")
    public void theClientPlacesAnOrderWithIdempotencyKey(String idempotencyKey) {
        ResponseEntity<Map> response = httpHelper.postOrder(
                "customer-obs", "SKU-OBS", 1, idempotencyKey);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("order request should succeed")
                .isTrue();
    }

    @When("the client retries the same order with idempotency key {string}")
    public void theClientRetriesTheSameOrder(String idempotencyKey) {
        ResponseEntity<Map> response = httpHelper.postOrder(
                "customer-obs", "SKU-OBS", 1, idempotencyKey);
        assertThat(response.getStatusCode().value())
                .as("duplicate order should return 409")
                .isEqualTo(409);
    }

    @Then("the actuator prometheus endpoint contains metric {string}")
    public void theActuatorPrometheusEndpointContainsMetric(String metricName) {
        prometheusResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(prometheusResponse.getStatusCode().is2xxSuccessful())
                .as("prometheus endpoint should be accessible")
                .isTrue();
        assertThat(prometheusResponse.getBody())
                .as("prometheus output should contain metric %s", metricName)
                .contains(metricName);
    }
}
