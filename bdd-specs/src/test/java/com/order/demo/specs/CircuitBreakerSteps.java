package com.order.demo.specs;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.cucumber.java.en.Given;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class CircuitBreakerSteps {

    @Given("inventory service is unavailable")
    public void inventoryServiceIsUnavailable() {
        PlaceOrderSteps.inventoryMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": false, \"reservationId\": null }")));
    }
}