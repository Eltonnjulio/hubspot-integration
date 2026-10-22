package br.com.meetime.hubspot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "http.client")
public record HttpClientProperties(Duration connectTimeout, Duration readTimeout) {
    public HttpClientProperties {
        Objects.requireNonNull(connectTimeout, "Connect timeout is required");
        Objects.requireNonNull(readTimeout, "Read timeout is required");
        if (connectTimeout.toMillis() <= 0 || readTimeout.toMillis() <= 0
                || connectTimeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("HTTP timeouts must be positive and connect timeout must fit an integer");
        }
    }
}
