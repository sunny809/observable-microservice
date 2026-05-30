package com.example.order.specs.config;

import com.example.order.specs.HttpHelper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BddTestConfig {

    @Bean
    HttpHelper httpHelper(TestRestTemplate restTemplate) {
        return new HttpHelper(restTemplate);
    }
}
