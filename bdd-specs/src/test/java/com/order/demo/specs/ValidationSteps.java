package com.order.demo.specs;

import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class ValidationSteps {

    @Autowired
    private HttpHelper httpHelper;

    private ResponseEntity<Map> response;

    @When("the client submits an order request with missing customerId")
    public void theClientSubmitsAnOrderRequestWithMissingCustomerId() {
        String body = "{\"idempotencyKey\":\"idem-1\",\"items\":[{\"sku\":\"sku-1\",\"quantity\":1}]}";
        response = httpHelper.postOrderRaw(body, httpHelper.defaultHeaders());
        PlaceOrderSteps.lastResponse = response;
    }

    @When("the client submits an order request with blank idempotencyKey")
    public void theClientSubmitsAnOrderRequestWithBlankIdempotencyKey() {
        String body = "{\"customerId\":\"customer-1\",\"idempotencyKey\":\"\",\"items\":[{\"sku\":\"sku-1\",\"quantity\":1}]}";
        response = httpHelper.postOrderRaw(body, httpHelper.defaultHeaders());
        PlaceOrderSteps.lastResponse = response;
    }

    @When("the client submits an order request with empty items list")
    public void theClientSubmitsAnOrderRequestWithEmptyItemsList() {
        String body = "{\"customerId\":\"customer-1\",\"idempotencyKey\":\"idem-1\",\"items\":[]}";
        response = httpHelper.postOrderRaw(body, httpHelper.defaultHeaders());
        PlaceOrderSteps.lastResponse = response;
    }
}