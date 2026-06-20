package com.example.order.specs;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PlaceOrderSteps {

    @Autowired
    private HttpHelper httpHelper;

    private ResponseEntity<Map> firstResponse;
    private ResponseEntity<Map> secondResponse;
    static ResponseEntity<Map> lastResponse;

    static final WireMockServer inventoryMock = new WireMockServer(8081);
    static final WireMockServer wmsMock = new WireMockServer(8082);
    static final WireMockServer tmsMock = new WireMockServer(8083);

    @Before
    public void setup() {
        if (!inventoryMock.isRunning()) {
            inventoryMock.start();
        }
        if (!wmsMock.isRunning()) {
            wmsMock.start();
        }
        if (!tmsMock.isRunning()) {
            tmsMock.start();
        }
        inventoryMock.resetAll();
        wmsMock.resetAll();
        tmsMock.resetAll();
    }

    @Given("inventory service returns reservation success")
    public void inventoryServiceReturnsReservationSuccess() {
        inventoryMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": true, \"reservationId\": \"resv-123\" }")));
    }

    @Given("inventory service returns reservation failure")
    public void inventoryServiceReturnsReservationFailure() {
        inventoryMock.stubFor(post(urlEqualTo("/api/inventory/reserve"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"success\": false, \"reservationId\": null }")));
    }

    @Given("WMS service accepts shipment instruction")
    public void wmsServiceAcceptsShipmentInstruction() {
        wmsMock.stubFor(post(urlEqualTo("/api/wms/shipments"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"accepted\": true, \"messageId\": \"wms-123\" }")));
    }

    @When("the client submits a place order request")
    public void theClientSubmitsAPlaceOrderRequest() {
        firstResponse = httpHelper.postOrder("customer-1", "sku-1", 2, "idem-key-1");
        lastResponse = firstResponse;
    }

    @When("the client submits a place order request with the same idempotency key twice")
    public void theClientSubmitsAPlaceOrderRequestWithTheSameIdempotencyKeyTwice() {
        firstResponse = httpHelper.postOrder("customer-1", "sku-1", 2, "idem-key-duplicate");
        secondResponse = httpHelper.postOrder("customer-1", "sku-1", 2, "idem-key-duplicate");
    }

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int statusCode) {
        ResponseEntity<Map> target = lastResponse;
        if (target == null) {
            target = secondResponse != null ? secondResponse : firstResponse;
        }
        assertThat(target.getStatusCode().value()).isEqualTo(statusCode);
    }

    @Then("the second response status should be 409")
    public void theSecondResponseStatusShouldBe409() {
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.getStatusCode().value()).isEqualTo(409);
    }

    @Then("the response should contain an orderId and traceId")
    public void theResponseShouldContainAnOrderIdAndTraceId() {
        assertThat(firstResponse.getBody()).containsKeys("orderId", "status", "traceId");
        assertThat(firstResponse.getHeaders().getFirst("X-Trace-Id")).isNotBlank();
    }

    @Then("the response should contain an error message")
    public void theResponseShouldContainAnErrorMessage() {
        ResponseEntity<Map> target = lastResponse;
        if (target == null) {
            target = secondResponse != null ? secondResponse : firstResponse;
        }
        assertThat(target.getBody()).containsKey("error");
    }

    @And("the inventory service should receive a reserve request")
    public void theInventoryServiceShouldReceiveAReserveRequest() {
        inventoryMock.verify(postRequestedFor(urlEqualTo("/api/inventory/reserve")));
    }

    @And("the WMS service should receive a shipment instruction")
    public void theWmsServiceShouldReceiveAShipmentInstruction() {
        wmsMock.verify(postRequestedFor(urlEqualTo("/api/wms/shipments")));
    }
}