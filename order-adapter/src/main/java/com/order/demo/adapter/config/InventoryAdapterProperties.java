package com.order.demo.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Inventory service adapter.
 *
 * <p>Reads {@code inventory.base-url} from application configuration.
 */
@Component
@ConfigurationProperties(prefix = "inventory")
public class InventoryAdapterProperties {

    private String baseUrl = "http://localhost:8081";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
