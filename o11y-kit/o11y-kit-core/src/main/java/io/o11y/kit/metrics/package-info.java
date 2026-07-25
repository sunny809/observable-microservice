/**
 * Business metrics SPI and Micrometer adapter.
 *
 * <p>Provides the {@link io.o11y.kit.metrics.BusinessMetricsPort} interface
 * for recording application-level metrics (counters, timers, gauges) without
 * coupling to a specific metrics backend, and a Micrometer-backed
 * implementation ({@link io.o11y.kit.metrics.MicrometerMetricsAdapter}).
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@link io.o11y.kit.metrics.BusinessMetricsPort} — the SPI interface</li>
 *   <li>{@link io.o11y.kit.metrics.MicrometerMetricsAdapter} — Micrometer implementation</li>
 * </ul>
 *
 * @since 0.5.0
 */
package io.o11y.kit.metrics;