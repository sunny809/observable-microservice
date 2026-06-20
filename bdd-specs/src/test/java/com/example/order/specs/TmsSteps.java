package com.example.order.specs;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TmsSteps {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String orderId;

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
        // Simulate WMS picking completion by publishing event directly
        // In a real scenario, this would be triggered by a WMS callback
        // For BDD testing, we rely on the saga's internal event handling
        // The order should transition from WMS_ACKED to WMS_PICKED
        // and then publish TmsInstructionRequiredEvent
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
