package com.order.demo.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the TMS service adapter.
 *
 * <p>Reads {@code tms.base-url} from application configuration.
 */
@Component
@ConfigurationProperties(prefix = "tms")
public class TmsAdapterProperties {

    private String baseUrl = "http://localhost:8083";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
