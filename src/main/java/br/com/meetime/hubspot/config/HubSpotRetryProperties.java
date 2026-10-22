package br.com.meetime.hubspot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "hubspot.retry")
public record HubSpotRetryProperties(int maxRetries, Duration initialDelay, Duration maxDelay) {
    public HubSpotRetryProperties {
        Objects.requireNonNull(initialDelay, "Initial retry delay is required");
        Objects.requireNonNull(maxDelay, "Maximum retry delay is required");
        if (maxRetries < 0 || maxRetries > 10 || initialDelay.isZero() || initialDelay.isNegative()
                || maxDelay.compareTo(initialDelay) < 0 || maxDelay.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Invalid HubSpot retry limits");
        }
    }
}
