package com.order.demo.specs;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TmsSteps {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HttpHelper httpHelper;

    private String orderId;
    private ResponseEntity<Map> callbackResponse;

    @Given("TMS service accepts dispatch instruction")
    public void tmsServiceAcceptsDispatchInstruction() {
        PlaceOrderSteps.tmsMock.stubFor(post(urlEqualTo("/api/tms/dispatches"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"accepted\": true, \"messageId\": \"tms-123\" }")));
    }

    @Given("TMS service rejects dispatch instruction")
    public void tmsServiceRejectsDispatchInstruction() {
        PlaceOrderSteps.tmsMock.stubFor(post(urlEqualTo("/api/tms/dispatches"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"accepted\": false, \"messageId\": \"tms-reject-1\" }")));
    }

    @Given("TMS service is unavailable")
    public void tmsServiceIsUnavailable() {
        PlaceOrderSteps.tmsMock.stubFor(post(urlEqualTo("/api/tms/dispatches"))
                .willReturn(aResponse()
                        .withFixedDelay(5000)
                        .withStatus(503)));
    }

    @When("WMS picking is completed for the order")
    public void wmsPickingIsCompletedForTheOrder() {
        orderId = (String) PlaceOrderSteps.lastResponse.getBody().get("orderId");
        callbackResponse = httpHelper.postWmsPickingCallback(orderId);
    }

    @When("the WMS callback is called with the order ID")
    public void wmsCallbackIsCalledWithOrderId() {
        orderId = (String) PlaceOrderSteps.lastResponse.getBody().get("orderId");
        callbackResponse = httpHelper.postWmsPickingCallback(orderId);
    }

    @When("the WMS callback is called with order ID {string}")
    public void wmsCallbackIsCalledWithOrderId(String predefinedOrderId) {
        callbackResponse = httpHelper.postWmsPickingCallback(predefinedOrderId);
    }

    @Then("the callback response status should be {int}")
    public void theCallbackResponseStatusShouldBe(int statusCode) {
        assertThat(callbackResponse.getStatusCode().value()).isEqualTo(statusCode);
    }

    @Given("a CREATED order exists in the database")
    public void aCreatedOrderExistsInTheDatabase() {
        jdbcTemplate.execute("INSERT INTO orders (id, customer_id, idempotency_key, " +
                "reservation_id, status, created_at, items, reservation_ids) " +
                "VALUES ('ord-pre-created', 'cust-pre', 'idem-pre', 'resv-pre', " +
                "'CREATED', NOW(), '[]', '[]')");
    }

    @When("the WMS callback is called with the pre-created order ID")
    public void wmsCallbackIsCalledWithPreCreatedOrderId() {
        callbackResponse = httpHelper.postWmsPickingCallback("ord-pre-created");
    }

    @Then("the order status should eventually be {string}")
    public void theOrderStatusShouldEventuallyBe(String expectedStatus) {
        if (orderId == null) {
            orderId = (String) PlaceOrderSteps.lastResponse.getBody().get("orderId");
        }
        await().atMost(10, SECONDS).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, orderId);
            assertThat(status).isEqualTo(expectedStatus);
        });
    }

    @And("the TMS service should receive a dispatch instruction")
    public void theTmsServiceShouldReceiveADispatchInstruction() {
        await().atMost(10, SECONDS).untilAsserted(() ->
                PlaceOrderSteps.tmsMock.verify(postRequestedFor(urlEqualTo("/api/tms/dispatches"))));
    }
}
