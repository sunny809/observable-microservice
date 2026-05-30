package com.example.order.specs;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

public class MultiItemSteps {

    @Autowired
    private HttpHelper httpHelper;

    private ResponseEntity<Map> response;

    @Given("inventory service returns reservation success for all items")
    public void inventoryServiceReturnsReservationSuccessForAllItems() {
        PlaceOrderSteps.inventoryMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": true, \"reservationId\": \"resv-multi\" }")));
    }

    @Given("inventory service returns success for first item and failure for second item")
    public void inventoryServiceReturnsSuccessForFirstItemAndFailureForSecondItem() {
        PlaceOrderSteps.inventoryMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .inScenario("Partial Failure")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": true, \"reservationId\": \"resv-first\" }"))
                .willSetStateTo("First Succeeded"));

        PlaceOrderSteps.inventoryMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .inScenario("Partial Failure")
                .whenScenarioStateIs("First Succeeded")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": false, \"reservationId\": null }")));

        PlaceOrderSteps.inventoryMock.stubFor(delete(urlPathMatching("/api/inventory/reserve/.*"))
                .willReturn(aResponse().withStatus(200)));
    }

    @When("the client submits a place order request with multiple items")
    public void theClientSubmitsAPlaceOrderRequestWithMultipleItems() {
        String itemsJson = "[{\"sku\":\"sku-1\",\"quantity\":1},{\"sku\":\"sku-2\",\"quantity\":2}]";
        response = httpHelper.postOrder("customer-multi", "idem-key-multi", itemsJson, httpHelper.defaultHeaders());
        PlaceOrderSteps.lastResponse = response;
    }

    @Then("the inventory service should receive {int} reserve requests")
    public void theInventoryServiceShouldReceiveReserveRequests(int count) {
        PlaceOrderSteps.inventoryMock.verify(exactly(count),
                postRequestedFor(urlEqualTo("/api/inventory/reserve")));
    }

    @And("the inventory service should receive a release request")
    public void theInventoryServiceShouldReceiveAReleaseRequest() {
        PlaceOrderSteps.inventoryMock.verify(deleteRequestedFor(urlPathMatching("/api/inventory/reserve/.*")));
    }
}