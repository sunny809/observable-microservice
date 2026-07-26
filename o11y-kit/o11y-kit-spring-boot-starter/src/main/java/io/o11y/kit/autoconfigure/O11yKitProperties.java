package io.o11y.kit.autoconfigure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for o11y-kit, bound under the {@code o11y.kit} prefix.
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
 * @since 0.2.0-alpha
 */
@ConfigurationProperties(prefix = "o11y.kit")
public class O11yKitProperties {

    @NestedConfigurationProperty
    private ClientProperties client = new ClientProperties();

    @NestedConfigurationProperty
    private Server server = new Server();

    public ClientProperties getClient() { return client; }
    public void setClient(ClientProperties client) { this.client = client; }

    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }

    /**
     * Configuration for inbound HTTP server observability features.
     */
    public static class Server {
        private boolean enabled = true;
        private List<String> excludePatterns = new java.util.ArrayList<>(
                java.util.List.of("/actuator/**", "/health/**"));
        @NestedConfigurationProperty
        private Metrics metrics = new Metrics();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getExcludePatterns() { return excludePatterns; }
        public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }
        public Metrics getMetrics() { return metrics; }
        public void setMetrics(Metrics metrics) { this.metrics = metrics; }

        public static class Metrics {
            private boolean enabled = true;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }
}