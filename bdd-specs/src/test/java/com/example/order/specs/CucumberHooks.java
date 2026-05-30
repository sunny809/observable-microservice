package com.example.order.specs;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class CucumberHooks {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Before
    public void resetCircuitBreakersBefore() {
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(cb -> cb.reset());
    }

    @After
    public void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM saga_logs");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM inventory_reservation");
    }

    @After
    public void resetCircuitBreakersAfter() {
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(cb -> cb.reset());
    }
}