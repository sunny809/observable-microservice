package io.o11y.kit.autoconfigure;

import java.util.ArrayList;
import java.util.List;

public class ClientProperties {
    private boolean enabled = true;
    private MetricsProperties metrics = new MetricsProperties();
    private List<String> ignorePatterns = new ArrayList<>(List.of(
            "/actuator/**", "/health/**", "/info/**", "/prometheus/**"
    ));
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public MetricsProperties getMetrics() { return metrics; }
    public void setMetrics(MetricsProperties metrics) { this.metrics = metrics; }
    public List<String> getIgnorePatterns() { return ignorePatterns; }
    public void setIgnorePatterns(List<String> ignorePatterns) { this.ignorePatterns = ignorePatterns; }
}
