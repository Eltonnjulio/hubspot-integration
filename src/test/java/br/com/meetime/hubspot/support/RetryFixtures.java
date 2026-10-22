package br.com.meetime.hubspot.support;

import br.com.meetime.hubspot.client.HubSpotRetry;
import br.com.meetime.hubspot.config.HubSpotRetryProperties;
import java.time.Clock;
import java.time.Duration;

public final class RetryFixtures {
    private RetryFixtures() {}

    public static HubSpotRetry disabled() {
        return new HubSpotRetry(new HubSpotRetryProperties(0, Duration.ofSeconds(1), Duration.ofSeconds(60)), Clock.systemUTC());
    }

    public static HubSpotRetry enabled() {
        return new HubSpotRetry(new HubSpotRetryProperties(3, Duration.ofSeconds(1), Duration.ofSeconds(60)), Clock.systemUTC());
    }
}
