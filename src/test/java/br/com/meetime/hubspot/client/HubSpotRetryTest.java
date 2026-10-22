package br.com.meetime.hubspot.client;

import br.com.meetime.hubspot.config.HubSpotRetryProperties;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HubSpotRetryTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final HubSpotRetry retry = new HubSpotRetry(
            new HubSpotRetryProperties(3, Duration.ofSeconds(1), Duration.ofSeconds(60)),
            Clock.fixed(now, ZoneOffset.UTC));

    private HubSpotApiException limited(String delay) {
        return new HubSpotApiException("limited", HttpStatus.TOO_MANY_REQUESTS, null, delay);
    }

    @Test
    void respectsRetryAfterSecondsBeforeRecovering() {
        AtomicInteger calls = new AtomicInteger();
        StepVerifier.withVirtualTime(() -> Mono.defer(() -> calls.incrementAndGet() == 1
                        ? Mono.<String>error(limited("30")) : Mono.just("ok")).retryWhen(retry.policy()))
                .expectSubscription().expectNoEvent(Duration.ofSeconds(29))
                .then(() -> assertEquals(1, calls.get())).thenAwait(Duration.ofSeconds(1))
                .expectNext("ok").verifyComplete();
        assertEquals(2, calls.get());
    }

    @Test
    void supportsHttpDate() {
        AtomicInteger calls = new AtomicInteger();
        StepVerifier.withVirtualTime(() -> Mono.defer(() -> calls.incrementAndGet() == 1
                        ? Mono.<String>error(limited("Thu, 1 Jan 2026 00:00:05 GMT")) : Mono.just("ok"))
                        .retryWhen(retry.policy()))
                .expectSubscription().expectNoEvent(Duration.ofSeconds(4)).thenAwait(Duration.ofSeconds(1))
                .expectNext("ok").verifyComplete();
    }

    @Test
    void exponentialBackoffExhaustionPreservesOriginalError() {
        AtomicInteger calls = new AtomicInteger();
        var failure = limited("invalid");
        StepVerifier.withVirtualTime(() -> Mono.defer(() -> {
                    calls.incrementAndGet();
                    return Mono.error(failure);
                }).retryWhen(retry.policy()))
                .expectSubscription().thenAwait(Duration.ofSeconds(7))
                .expectErrorMatches(error -> error == failure).verify();
        assertEquals(4, calls.get());
    }

    @Test
    void excessiveServerDelayIsNotShortened() {
        AtomicInteger calls = new AtomicInteger();
        var failure = limited("3600");
        StepVerifier.create(Mono.defer(() -> {
            calls.incrementAndGet();
            return Mono.error(failure);
        }).retryWhen(retry.policy())).expectErrorMatches(error -> error == failure).verify();
        assertEquals(1, calls.get());
    }

    @Test
    void otherHttpErrorsAndNetworkFailuresAreNotRetried() {
        for (Throwable error : new Throwable[] {
                new HubSpotApiException("invalid", HttpStatus.BAD_REQUEST),
                new HubSpotApiException("unavailable", HttpStatus.SERVICE_UNAVAILABLE),
                new java.io.IOException("connection lost")}) {
            AtomicInteger calls = new AtomicInteger();
            StepVerifier.create(Mono.defer(() -> {
                calls.incrementAndGet();
                return Mono.error(error);
            }).retryWhen(retry.policy())).expectErrorMatches(actual -> actual == error).verify();
            assertEquals(1, calls.get());
        }
    }

    @Test
    void cancellationStopsPendingRetries() {
        AtomicInteger calls = new AtomicInteger();
        StepVerifier.withVirtualTime(() -> Mono.defer(() -> {
            calls.incrementAndGet();
            return Mono.error(limited("30"));
        }).retryWhen(retry.policy())).expectSubscription().thenAwait(Duration.ofSeconds(1))
                .thenCancel().verify();
        assertEquals(1, calls.get());
    }
}
