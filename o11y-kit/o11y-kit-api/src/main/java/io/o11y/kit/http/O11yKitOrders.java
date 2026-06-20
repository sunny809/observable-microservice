package io.o11y.kit.http;

/**
 * Centralized order constants for o11y-kit components that participate in
 * an ordered chain (e.g. Spring filters, interceptors, observation handlers,
 * WebClient filters).
 *
 * <p>Values in this class are framework-agnostic by design — {@code o11y-kit-api}
 * is a zero-dependency module. Values are nevertheless chosen so that, when
 * adapted into a Spring {@code Ordered} chain, they sit in sensible positions
 * relative to {@code org.springframework.core.Ordered}:
 *
 * <ul>
 *   <li>{@link #CLIENT_OBSERVATION} sits 100 positions ahead of
 *       {@code Ordered.LOWEST_PRECEDENCE}, so that user-supplied client
 *       interceptors with default ordering still execute after o11y-kit's
 *       observation handler.</li>
 * </ul>
 *
 * <p>This class is a constants holder and cannot be instantiated.
 *
 * @since 0.2.0-alpha
 */
public final class O11yKitOrders {

    /**
     * Order constant for the o11y-kit client-side observation handler /
     * filter / interceptor.
     *
     * <p>Equivalent to {@code org.springframework.core.Ordered.LOWEST_PRECEDENCE - 100}
     * (i.e. {@code Integer.MAX_VALUE - 100}). Encoding the value as a literal here
     * lets {@code o11y-kit-api} stay free of Spring framework dependencies while
     * remaining drop-in usable in Spring {@code @Order} contexts.
     */
    public static final int CLIENT_OBSERVATION = Integer.MAX_VALUE - 100;

    private O11yKitOrders() {
        // Constants holder — not instantiable.
    }
}
