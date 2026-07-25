package io.o11y.kit.spring.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks a Spring-managed method for observation.
 *
 * <p>When a method annotated with {@code @Observed} is invoked, an AOP aspect
 * automatically records its execution duration as a Micrometer
 * {@link io.micrometer.core.instrument.Timer} under the metric name
 * {@code o11y.observed.duration}.
 *
 * <p><b>Basic usage:</b>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     @Observed
 *     public String doSomething() { ... }
 * }
 * }</pre>
 *
 * <p><b>Custom metric name:</b>
 * <pre>{@code
 * @Observed(name = "my.custom.metric")
 * public String doSomething() { ... }
 * }</pre>
 *
 * <p><b>Custom tags:</b>
 * <pre>{@code
 * @Observed(tags = {"region", "us-east-1", "env", "prod"})
 * public String doSomething() { ... }
 * }</pre>
 *
 * <p>Requires {@code spring-boot-starter-aop} (or equivalent AspectJ weaving)
 * on the classpath for the AOP advice to activate.
 *
 * @since 0.4.0-beta
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Observed {

    /**
     * Override the metric name (defaults to {@code o11y.observed.duration}).
     *
     * @return the metric name
     */
    String name() default "";

    /**
     * Additional metric tags in key-value pairs.
     *
     * <p>Array must contain an even number of strings:
     * {@code {"key1", "val1", "key2", "val2"}}.
     *
     * @return the metric tags (empty by default)
     */
    String[] tags() default {};

    /**
     * Description for the Micrometer metric.
     *
     * @return the description (empty by default)
     */
    String description() default "";
}