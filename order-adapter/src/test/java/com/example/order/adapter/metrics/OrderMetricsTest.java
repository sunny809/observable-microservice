package com.example.order.adapter.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class OrderMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private OrderMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new OrderMetrics(meterRegistry);
    }

    @Test
    @DisplayName("recordOrderPlaced registers orders.placed counter with status tag")
    void recordOrderPlaced_registersCounter() {
        metrics.recordOrderPlaced("CREATED");

        Collection<Meter> meters = meterRegistry.getMeters();
        assertThat(meters).anyMatch(m -> m.getId().getName().equals("orders.placed")
                && m.getId().getTag("status").equals("CREATED"));
    }

    @Test
    @DisplayName("recordOrderFailed registers orders.failed counter with reason tag")
    void recordOrderFailed_registersCounter() {
        metrics.recordOrderFailed("INSUFFICIENT_INVENTORY");

        assertThat(meterRegistry.getMeters()).anyMatch(m ->
                m.getId().getName().equals("orders.failed")
                && m.getId().getTag("reason").equals("INSUFFICIENT_INVENTORY"));
    }

    @Test
    @DisplayName("recordOrderFailed with DUPLICATE_ORDER registers correct tag")
    void recordOrderFailed_duplicate_registersCounter() {
        metrics.recordOrderFailed("DUPLICATE_ORDER");

        assertThat(meterRegistry.getMeters()).anyMatch(m ->
                m.getId().getName().equals("orders.failed")
                && m.getId().getTag("reason").equals("DUPLICATE_ORDER"));
    }

    @Test
    @DisplayName("recordInventoryReservation registers counter with sku and result tags")
    void recordInventoryReservation_registersCounter() {
        metrics.recordInventoryReservation("SKU-1", true);
        metrics.recordInventoryReservation("SKU-1", false);

        assertThat(meterRegistry.getMeters()).anyMatch(m ->
                m.getId().getName().equals("inventory.reservation")
                && "SKU-1".equals(m.getId().getTag("sku"))
                && "success".equals(m.getId().getTag("result")));
        assertThat(meterRegistry.getMeters()).anyMatch(m ->
                m.getId().getName().equals("inventory.reservation")
                && "SKU-1".equals(m.getId().getTag("sku"))
                && "failure".equals(m.getId().getTag("result")));
    }

    @Test
    @DisplayName("recordSagaDuration registers saga.duration timer with outcome tag")
    void recordSagaDuration_registersTimer() {
        metrics.recordSagaDuration(150, "success");

        assertThat(meterRegistry.getMeters()).anyMatch(m ->
                m.getId().getName().equals("saga.duration")
                && m.getId().getTag("outcome").equals("success"));
    }

    @Test
    @DisplayName("recordSagaStepDuration registers saga.step.duration timer with step and outcome tags")
    void recordSagaStepDuration_registersTimer() {
        metrics.recordSagaStepDuration("WMS_ACKED", 200, "success");

        assertThat(meterRegistry.getMeters()).anyMatch(m ->
                m.getId().getName().equals("saga.step.duration")
                && "WMS_ACKED".equals(m.getId().getTag("step"))
                && "success".equals(m.getId().getTag("outcome")));
    }

    @Test
    @DisplayName("recordSagaStepDuration with failure outcome registers correct tag")
    void recordSagaStepDuration_failure_registersTimer() {
        metrics.recordSagaStepDuration("TMS_DISPATCHED", 50, "failure");

        assertThat(meterRegistry.getMeters()).anyMatch(m ->
                m.getId().getName().equals("saga.step.duration")
                && "failure".equals(m.getId().getTag("outcome")));
    }

    @Test
    @DisplayName("recordSagaGap registers saga.gap.duration timer with gap tag")
    void recordSagaGap_registersTimer() {
        metrics.recordSagaGap("POST_COMMIT_TO_WMS", 30);

        assertThat(meterRegistry.getMeters()).anyMatch(m ->
                m.getId().getName().equals("saga.gap.duration")
                && "POST_COMMIT_TO_WMS".equals(m.getId().getTag("gap")));
    }

    @Test
    @DisplayName("multiple calls increment counters and record multiple timer samples")
    void multipleCalls_accumulate() {
        metrics.recordOrderPlaced("CREATED");
        metrics.recordOrderPlaced("CREATED");
        metrics.recordOrderPlaced("WMS_ACKED");

        // SimpleMeterRegistry counters are real — find and check count
        assertThat(meterRegistry.find("orders.placed").counters().stream()
                .filter(c -> "CREATED".equals(c.getId().getTag("status")))
                .findFirst()
                .map(c -> c.count() == 2)
                .orElse(false)).isTrue();
    }
}
