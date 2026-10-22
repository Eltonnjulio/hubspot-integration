package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.config.HubSpotConfig;
import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OAuthHttpContractTest {
    private HubSpotConfig config() {
        var config = new HubSpotConfig();
        var oauth = new HubSpotConfig.OAuthConfig();
        oauth.setClientId("id");
        oauth.setClientSecret("secret");
        oauth.setRedirectUri("https://example.com/callback");
        config.setOauth(oauth);
        return config;
    }

    @Test
    void concurrentSubscribersShareRefreshAndPreserveRotatedToken() {
        var storage = new TokenStorageServiceImpl(new br.com.meetime.hubspot.support.InMemoryTokenRepository(), java.time.Clock.systemUTC());
        var original = new HubSpotTokenResponse();
        original.setAccessToken("access");
        original.setRefreshToken("original-refresh");
        original.setExpiresIn(3600);
        storage.saveToken(original);
        var requests = new AtomicInteger();
        Sinks.One<ClientResponse> response = Sinks.one();
        Sinks.One<Boolean> requested = Sinks.one();
        var client = WebClient.builder().baseUrl("https://example.com/token").exchangeFunction(request -> {
            assertEquals("POST", request.method().name());
            requests.incrementAndGet();
            requested.tryEmitValue(true);
            return response.asMono();
        }).build();
        var service = new HubSpotOAuthServiceImpl(client, config(), storage, br.com.meetime.hubspot.support.RetryFixtures.disabled());
        var first = service.refreshToken();
        var second = service.refreshToken();
        StepVerifier.create(Mono.zip(first, second))
                .then(() -> {
                    assertEquals(Boolean.TRUE, requested.asMono().block(java.time.Duration.ofSeconds(5)));
                    assertEquals(1, requests.get());
                    response.tryEmitValue(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"access_token\":\"new-access\",\"refresh_token\":\"rotated\",\"expires_in\":3600}").build());
                })
                .expectNextMatches(tokens -> tokens.getT1().getAccessToken().equals("new-access")
                        && tokens.getT2().getAccessToken().equals("new-access"))
                .verifyComplete();
        assertEquals("rotated", storage.getRefreshToken().orElseThrow());
    }

    @Test
    void emptyErrorBodyStillPreservesHttpStatus() {
        var client = WebClient.builder().baseUrl("https://example.com/token")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build())).build();
        var service = new HubSpotOAuthServiceImpl(client, config(), new TokenStorageServiceImpl(new br.com.meetime.hubspot.support.InMemoryTokenRepository(), java.time.Clock.systemUTC()), br.com.meetime.hubspot.support.RetryFixtures.disabled());
        StepVerifier.create(service.exchangeCodeForToken("code"))
                .expectErrorMatches(error -> error instanceof HubSpotApiException api
                        && api.getStatusCode().value() == 429).verify();
    }
    @Test
    void expiredTokenIsAutomaticallyRefreshedBeforeUse() {
        var clock = org.mockito.Mockito.mock(java.time.Clock.class);
        var now = java.time.Instant.parse("2026-01-01T00:00:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        var storage = new TokenStorageServiceImpl(new br.com.meetime.hubspot.support.InMemoryTokenRepository(), clock);
        var original = new HubSpotTokenResponse();
        original.setAccessToken("expired-access");
        original.setRefreshToken("refresh");
        original.setExpiresIn(60);
        storage.saveToken(original);
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(61));
        var requests = new AtomicInteger();
        var client = WebClient.builder().baseUrl("https://example.com/token").exchangeFunction(request -> {
            requests.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"access_token\":\"renewed\",\"expires_in\":3600}").build());
        }).build();
        var service = new HubSpotOAuthServiceImpl(client, config(), storage,
                br.com.meetime.hubspot.support.RetryFixtures.enabled());
        StepVerifier.create(service.getValidAccessToken()).expectNext("renewed").verifyComplete();
        assertEquals("renewed", storage.getAccessToken().orElseThrow());
        assertEquals("refresh", storage.getRefreshToken().orElseThrow());
        assertEquals(1, requests.get());
    }
}
