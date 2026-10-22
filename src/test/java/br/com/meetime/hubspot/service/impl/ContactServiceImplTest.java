package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.domain.request.ContactCreateRequest;
import br.com.meetime.hubspot.domain.request.ContactPropertiesRequest;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.HubSpotOAuthService;
import br.com.meetime.hubspot.support.StubHttpConnector;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactServiceImplTest {
    private final HubSpotOAuthService oauth = mock(HubSpotOAuthService.class);

    private RateLimiter limiter() {
        return RateLimiter.of("test", RateLimiterConfig.custom().limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofHours(1)).timeoutDuration(Duration.ZERO).build());
    }

    private ContactCreateRequest contact() {
        var properties = new ContactPropertiesRequest();
        properties.setEmail("test@example.com");
        return new ContactCreateRequest(properties);
    }

    private ContactServiceImpl service(StubHttpConnector connector, RateLimiter limiter) {
        when(oauth.getValidAccessToken()).thenReturn(Mono.just("access-token"));
        return new ContactServiceImpl(WebClient.builder().baseUrl("https://example.com")
                .clientConnector(connector).build(), oauth, limiter, br.com.meetime.hubspot.support.RetryFixtures.disabled());
    }

    @Test
    void sendsAuthenticatedJsonAndDecodesContact() {
        AtomicReference<StubHttpConnector.Request> captured = new AtomicReference<>();
        var connector = new StubHttpConnector(request -> {
            captured.set(request);
            var response = new MockClientHttpResponse(HttpStatus.CREATED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.setBody("{\"id\":\"123\"}");
            return response;
        });
        StepVerifier.create(service(connector, limiter()).createContact(contact()))
                .expectNextMatches(response -> response.getId().equals("123")).verifyComplete();
        assertEquals("POST", captured.get().method().name());
        assertEquals("/crm/v3/objects/contacts", captured.get().uri().getPath());
        assertEquals("Bearer access-token", captured.get().headers().getFirst("Authorization"));
        assertTrue(captured.get().body().contains("test@example.com"));
    }

    @Test
    void rateLimitIsAppliedForEachSubscriptionWithoutSendingSecondRequest() {
        var connector = new StubHttpConnector(request -> {
            var response = new MockClientHttpResponse(HttpStatus.CREATED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.setBody("{\"id\":\"123\"}");
            return response;
        });
        var result = service(connector, limiter()).createContact(contact());
        StepVerifier.create(result).expectNextCount(1).verifyComplete();
        StepVerifier.create(result).expectError(RequestNotPermitted.class).verify();
        assertEquals(1, connector.calls());
    }

    @Test
    void preservesRateLimitStatusAndRetryAfterEvenForHtmlError() {
        var connector = new StubHttpConnector(request -> {
            var response = new MockClientHttpResponse(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.TEXT_HTML);
            response.getHeaders().set("Retry-After", "30");
            response.setBody("<html>Unavailable</html>");
            return response;
        });
        StepVerifier.create(service(connector, limiter()).createContact(contact()))
                .expectErrorMatches(error -> error instanceof HubSpotApiException api
                        && api.getStatusCode().value() == 429 && "30".equals(api.getRetryAfter())).verify();
        assertEquals(1, connector.calls());
    }

    @Test
    void emptySuccessfulResponseIsAnUpstreamError() {
        var connector = new StubHttpConnector(request -> new MockClientHttpResponse(HttpStatus.CREATED));
        StepVerifier.create(service(connector, limiter()).createContact(contact()))
                .expectErrorMatches(error -> error instanceof HubSpotApiException api
                        && api.getStatusCode().value() == 502).verify();
    }
    @Test
    void recoversFromHubSpot429AndChecksTokenForEachAttempt() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var connector = new StubHttpConnector(request -> {
            if (attempts.incrementAndGet() == 1) {
                var limited = new MockClientHttpResponse(HttpStatus.TOO_MANY_REQUESTS);
                limited.getHeaders().set("Retry-After", "2");
                return limited;
            }
            var response = new MockClientHttpResponse(HttpStatus.CREATED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.setBody("{\"id\":\"recovered\"}");
            assertEquals("Bearer refreshed", request.headers().getFirst("Authorization"));
            return response;
        });
        when(oauth.getValidAccessToken()).thenReturn(Mono.just("initial"), Mono.just("refreshed"));
        var permits = mock(RateLimiter.class);
        when(permits.acquirePermission()).thenReturn(true);
        var service = new ContactServiceImpl(WebClient.builder().baseUrl("https://example.com")
                .clientConnector(connector).build(), oauth, permits,
                br.com.meetime.hubspot.support.RetryFixtures.enabled());
        StepVerifier.withVirtualTime(() -> service.createContact(contact()))
                .expectSubscription().expectNoEvent(Duration.ofSeconds(1))
                .thenAwait(Duration.ofSeconds(1))
                .expectNextMatches(response -> response.getId().equals("recovered")).verifyComplete();
        assertEquals(2, connector.calls());
        verify(oauth, times(2)).getValidAccessToken();
        verify(permits, times(2)).acquirePermission();
    }
}
