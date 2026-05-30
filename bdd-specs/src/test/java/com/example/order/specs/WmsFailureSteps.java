package com.example.order.specs;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class WmsFailureSteps {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String orderId;

    @Given("WMS service rejects shipment instruction")
    public void wmsServiceRejectsShipmentInstruction() {
        PlaceOrderSteps.wmsMock.stubFor(post(urlEqualTo("/api/wms/shipments"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"accepted\": false, \"messageId\": \"wms-reject-1\" }")));
    }

    @Given("WMS service is unavailable")
    public void wmsServiceIsUnavailable() {
        PlaceOrderSteps.wmsMock.stubFor(post(urlEqualTo("/api/wms/shipments"))
                .willReturn(aResponse()
                        .withFixedDelay(5000)
                        .withStatus(503)));
    }

    @Then("the sync response status should be 201")
    public void theSyncResponseStatusShouldBe201() {
        assertThat(PlaceOrderSteps.lastResponse.getStatusCode().value()).isEqualTo(201);
        orderId = (String) PlaceOrderSteps.lastResponse.getBody().get("orderId");
    }

    @And("the inventory service should eventually release the reservation")
    public void theInventoryServiceShouldEventuallyReleaseTheReservation() {
        await().atMost(10, SECONDS).untilAsserted(() ->
                PlaceOrderSteps.inventoryMock.verify(deleteRequestedFor(urlPathMatching("/api/inventory/reserve/.*"))));
    }

    @And("the order status should eventually be REJECTED")
    public void theOrderStatusShouldEventuallyBeRejected() {
        await().atMost(10, SECONDS).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, orderId);
            assertThat(status).isEqualTo("REJECTED");
        });
    }
}