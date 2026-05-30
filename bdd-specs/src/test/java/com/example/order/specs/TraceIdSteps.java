package com.example.order.specs;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceIdSteps {

    @Autowired
    private HttpHelper httpHelper;

    private ResponseEntity<Map> response;
    private String sentTraceId;

    @When("the client submits a place order request with X-B3-TraceId")
    public void theClientSubmitsAPlaceOrderRequestWithXB3TraceId() {
        sentTraceId = "test-b3-trace-id-" + System.nanoTime();
        response = httpHelper.postOrder("customer-trace", "sku-trace", 1, "idem-key-trace-" + System.nanoTime(),
                httpHelper.headersWithXB3TraceId(sentTraceId));
    }

    @When("the client submits a place order request with traceparent header")
    public void theClientSubmitsAPlaceOrderRequestWithTraceparent() {
        sentTraceId = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f042889e-01";
        response = httpHelper.postOrder("customer-tp", "sku-tp", 1, "idem-key-tp-" + System.nanoTime(),
                httpHelper.headersWithTraceparent(sentTraceId));
    }

    @Then("the response should contain the same trace ID in X-Trace-Id header")
    public void theResponseShouldContainTheSameTraceIdInXTraceIdHeader() {
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo(sentTraceId);
    }

    @Then("the response should contain a trace ID in X-Trace-Id header")
    public void theResponseShouldContainATraceIdInXTraceIdHeader() {
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isNotBlank();
    }
}