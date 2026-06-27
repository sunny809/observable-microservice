package com.example.order.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the WMS service adapter.
 *
 * <p>Reads {@code wms.base-url} from application configuration.
 */
@Component
@ConfigurationProperties(prefix = "wms")
public class WmsAdapterProperties {

    private String baseUrl = "http://localhost:8082";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
