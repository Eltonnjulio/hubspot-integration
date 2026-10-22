package br.com.meetime.hubspot.client;

import br.com.meetime.hubspot.config.HubSpotRetryProperties;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@EnableConfigurationProperties(HubSpotRetryProperties.class)
public class HubSpotRetry {
    private final HubSpotRetryProperties properties;
    private final Clock clock;

    public HubSpotRetry(HubSpotRetryProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Retry policy() {
        return Retry.from(signals -> signals.concatMap(signal -> {
            Throwable failure = signal.failure();
            if (!(failure instanceof HubSpotApiException error) || error.getStatusCode().value() != 429
                    || signal.totalRetries() >= properties.maxRetries()) {
                return Mono.error(failure);
            }
            Duration delay = delay(error.getRetryAfter(), signal.totalRetries());
            if (delay.compareTo(properties.maxDelay()) > 0) {
                return Mono.error(failure);
            }
            return Mono.delay(delay);
        }));
    }

    private Duration delay(String retryAfter, long retry) {
        if (retryAfter != null) {
            try {
                Duration delay = retryAfter.matches("[0-9]{1,9}")
                        ? Duration.ofSeconds(Long.parseLong(retryAfter))
                        : Duration.between(Instant.now(clock), ZonedDateTime.parse(retryAfter,
                                DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (DateTimeParseException | NumberFormatException ignored) {
            }
        }
        Duration backoff = properties.initialDelay().multipliedBy(1L << retry);
        return backoff.compareTo(properties.maxDelay()) > 0 ? properties.maxDelay() : backoff;
    }
}
