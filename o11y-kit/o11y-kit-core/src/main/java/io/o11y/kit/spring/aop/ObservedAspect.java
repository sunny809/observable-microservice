package io.o11y.kit.spring.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * AOP aspect that intercepts methods annotated with {@link Observed} and records
 * execution duration as a Micrometer {@link Timer}.
 *
 * <p>The aspect creates a timer named {@code o11y.observed.duration} with tags
 * for the class name, method name, outcome (success/error), and any user-defined
 * tags from the annotation.
 *
 * <p>This aspect requires {@code spring-boot-starter-aop} on the classpath.
 *
 * @since 0.4.0-beta
 */
@Aspect
public class ObservedAspect {

    private static final String METRIC_NAME = "o11y.observed.duration";
    private static final String TAG_CLASS = "class";
    private static final String TAG_METHOD = "method";
    private static final String TAG_OUTCOME = "outcome";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_ERROR = "error";

    private final MeterRegistry meterRegistry;

    /**
     * Creates a new {@code ObservedAspect} with the given meter registry.
     *
     * @param meterRegistry the Micrometer registry to register timers with;
     *                      must not be null
     */
    public ObservedAspect(MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            throw new IllegalArgumentException("MeterRegistry must not be null");
        }
        this.meterRegistry = meterRegistry;
    }

    /**
     * Intercepts any method annotated with {@link Observed} and records its
     * execution duration.
     *
     * @param pjp the join point for the intercepted method
     * @return the result of the method invocation
     * @throws Throwable if the intercepted method throws an exception
     */
    @Around("@annotation(io.o11y.kit.spring.aop.Observed)")
    public Object observeAndRecord(ProceedingJoinPoint pjp) throws Throwable {
        Observed observed = resolveAnnotation(pjp);
        String metricName = resolveMetricName(pjp, observed);
        List<Tag> tags = buildTags(pjp, observed);

        long startNanos = System.nanoTime();
        String outcome = OUTCOME_SUCCESS;
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            outcome = OUTCOME_ERROR;
            throw ex;
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            List<Tag> outcomeTags = new ArrayList<>(tags);
            outcomeTags.add(Tag.of(TAG_OUTCOME, outcome));
            Timer.builder(metricName)
                    .tags(outcomeTags)
                    .description(resolveDescription(observed))
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Resolves the {@link Observed} annotation from the method.
     * Falls back to the class-level annotation if present.
     */
    private static Observed resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Observed observed = AnnotationUtils.findAnnotation(method, Observed.class);
        if (observed == null) {
            // Should not happen given the @Around pointcut, but guard anyway
            throw new IllegalStateException(
                    "@Observed annotation not found on " + method);
        }
        return observed;
    }

    /**
     * Resolves the metric name. If the annotation specifies a custom name,
     * that is used; otherwise defaults to {@code o11y.observed.duration}.
     */
    private static String resolveMetricName(ProceedingJoinPoint pjp, Observed observed) {
        if (!observed.name().isEmpty()) {
            return observed.name();
        }
        return METRIC_NAME;
    }

    /**
     * Builds the tag list for the timer, including class, method, and user-defined
     * tags. The outcome tag is added separately during recording.
     */
    private static List<Tag> buildTags(ProceedingJoinPoint pjp, Observed observed) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(TAG_CLASS, pjp.getTarget().getClass().getSimpleName()));
        tags.add(Tag.of(TAG_METHOD, pjp.getSignature().getName()));

        // User-defined tags (key-value pairs)
        String[] userTags = observed.tags();
        if (userTags.length > 0) {
            if (userTags.length % 2 != 0) {
                throw new IllegalArgumentException(
                        "@Observed tags must be key-value pairs (even length), got: "
                                + userTags.length);
            }
            for (int i = 0; i < userTags.length; i += 2) {
                tags.add(Tag.of(userTags[i], userTags[i + 1]));
            }
        }

        return tags;
    }

    /**
     * Resolves the metric description from the annotation.
     */
    private static String resolveDescription(Observed observed) {
        if (!observed.description().isEmpty()) {
            return observed.description();
        }
        return "Duration of @Observed-annotated methods";
    }
}
