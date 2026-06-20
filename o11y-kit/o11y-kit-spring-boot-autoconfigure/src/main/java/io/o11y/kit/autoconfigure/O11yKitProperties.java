package io.o11y.kit.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for o11y-kit, bound under the {@code o11y.kit} prefix.
 *
 * <p>The properties are organized into two namespaces:
 * <ul>
 *   <li>{@link Client} — outbound HTTP observability (WebClient / RestTemplate /
 *       RestClient interceptors and metrics).</li>
 *   <li>{@link Server} — inbound HTTP observability (server-side handlers and
 *       interceptors). Reserved as a namespace placeholder for this sprint.</li>
 * </ul>
 *
 * <p>Example {@code application.yml}:
 * <pre>{@code
 * o11y:
 *   kit:
 *     client:
 *       enabled: true
 *       metrics:
 *         enabled: true
 * }</pre>
 *
 * <p>This type is registered with Spring Boot's auto-configuration via
 * {@link HttpMetricsAutoConfiguration}'s {@code @EnableConfigurationProperties}.
 *
 * @since 0.2.0-alpha
 */
@ConfigurationProperties(prefix = "o11y.kit")
public class O11yKitProperties {

    /**
     * Configuration for outbound HTTP client observability.
     */
    @NestedConfigurationProperty
    private Client client = new Client();

    /**
     * Configuration for inbound HTTP server observability.
     *
     * <p>Reserved namespace; no fields are read from configuration this sprint.
     */
    @NestedConfigurationProperty
    private Server server = new Server();

    /**
     * Returns the client-side observability configuration.
     *
     * @return the client configuration (never {@code null})
     */
    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    /**
     * Returns the server-side observability configuration.
     *
     * @return the server configuration (never {@code null})
     */
    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    /**
     * Configuration for outbound HTTP client observability features.
     *
     * @since 0.2.0-alpha
     */
    public static class Client {

        /**
         * Master switch for all client-side observability features.
         *
         * <p>When {@code false}, the auto-configuration must skip wiring any
         * client-side interceptors, filters, or handlers. Defaults to {@code true}.
         */
        private boolean enabled = true;

        /**
         * Configuration for client-side HTTP metrics.
         */
        @NestedConfigurationProperty
        private Metrics metrics = new Metrics();

        /**
         * Returns whether client-side observability is enabled.
         *
         * @return {@code true} if enabled (the default), {@code false} otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the client metrics configuration.
         *
         * @return the metrics configuration (never {@code null})
         */
        public Metrics getMetrics() {
            return metrics;
        }

        public void setMetrics(Metrics metrics) {
            this.metrics = metrics;
        }

        /**
         * Configuration for client-side HTTP metrics emission.
         *
         * @since 0.2.0-alpha
         */
        public static class Metrics {

            /**
             * Whether client-side HTTP metrics emission is enabled.
             *
             * <p>Defaults to {@code true}. When {@code false}, client interceptors
             * remain installed (for tracing/correlation) but emit no Micrometer
             * timers or counters.
             */
            private boolean enabled = true;

            /**
             * Returns whether client metrics emission is enabled.
             *
             * @return {@code true} if enabled (the default), {@code false} otherwise
             */
            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

    /**
     * Configuration for inbound HTTP server observability features.
     *
     * <p>This class reserves the {@code o11y.kit.server} namespace for future
     * sprint work (handler enable/disable flags, exclusion patterns, etc.).
     * No fields are bound this sprint — declaring the placeholder here lets
     * users add the namespace to their {@code application.yml} without Spring
     * Boot reporting an "unknown property" warning once future fields land.
     *
     * @since 0.2.0-alpha
     */
    public static class Server {
        // Intentionally empty — namespace reservation only.
    }
}
