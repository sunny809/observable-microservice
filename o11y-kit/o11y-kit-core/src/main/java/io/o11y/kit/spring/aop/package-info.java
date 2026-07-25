/**
 * AOP-based method-level observation for Spring Boot applications.
 *
 * <p>Provides the {@link io.o11y.kit.spring.aop.Observed} annotation and
 * its AOP aspect ({@link io.o11y.kit.spring.aop.ObservedAspect}) for
 * automatic method execution duration recording via Micrometer.
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@link io.o11y.kit.spring.aop.Observed} — the annotation to place on methods</li>
 *   <li>{@link io.o11y.kit.spring.aop.ObservedAspect} — the {@code @Around} advice</li>
 *   <li>{@link io.o11y.kit.spring.aop.ObservedAutoConfiguration} — Spring Boot auto-config</li>
 * </ul>
 *
 * @since 0.4.0-beta
 */
package io.o11y.kit.spring.aop;