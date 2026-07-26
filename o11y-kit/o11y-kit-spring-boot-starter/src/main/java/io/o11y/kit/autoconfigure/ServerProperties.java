package io.o11y.kit.autoconfigure;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for inbound HTTP server observability.
 *
 * <p>Bound under {@code o11y.kit.server}.
 */
public class ServerProperties {

    private boolean enabled = true;

    private List<String> excludePatterns = new ArrayList<>(
            List.of("/actuator/**", "/health/**"));

    private MetricsProperties metrics = new MetricsProperties();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }

    public MetricsProperties getMetrics() { return metrics; }
    public void setMetrics(MetricsProperties metrics) { this.metrics = metrics; }
}