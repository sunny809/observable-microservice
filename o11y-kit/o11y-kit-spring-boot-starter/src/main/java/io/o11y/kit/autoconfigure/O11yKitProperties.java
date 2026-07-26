package io.o11y.kit.autoconfigure;

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
 *     server:
 *       enabled: true
 * }</pre>
 *
 * @since 0.2.0-alpha
 */
@ConfigurationProperties(prefix = "o11y.kit")
public class O11yKitProperties {

    @NestedConfigurationProperty
    private ClientProperties client = new ClientProperties();

    @NestedConfigurationProperty
    private ServerProperties server = new ServerProperties();

    public ClientProperties getClient() { return client; }
    public void setClient(ClientProperties client) { this.client = client; }

    public ServerProperties getServer() { return server; }
    public void setServer(ServerProperties server) { this.server = server; }
}