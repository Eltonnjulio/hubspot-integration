package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.config.HubSpotConfig;
import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.TokenStorageService;
import br.com.meetime.hubspot.support.StubHttpConnector;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HubSpotOAuthServiceImplTest {
    private final TokenStorageService storage = mock(TokenStorageService.class);

    private HubSpotOAuthServiceImpl service(StubHttpConnector connector) {
        var config = new HubSpotConfig();
        var oauth = new HubSpotConfig.OAuthConfig();
        oauth.setClientId("client");
        oauth.setClientSecret("secret&value");
        oauth.setRedirectUri("https://example.com/callback");
        oauth.setAuthorizationUri("https://example.com/authorize");
        oauth.setScopes("scope1 scope2");
        config.setOauth(oauth);
        return new HubSpotOAuthServiceImpl(WebClient.builder().baseUrl("https://example.com/token")
                .clientConnector(connector).build(), config, storage, br.com.meetime.hubspot.support.RetryFixtures.disabled());
    }

    private MockClientHttpResponse tokenResponse() {
        var response = new MockClientHttpResponse(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.setBody("{\"access_token\":\"new-access\",\"refresh_token\":\"rotated\",\"expires_in\":3600}");
        return response;
    }

    @Test
    void authorizationEncodesScopesOnce() {
        String url = service(new StubHttpConnector(request -> tokenResponse())).getAuthorizationUrl();
        assertTrue(url.contains("scope=scope1%20scope2"));
        assertFalse(url.contains("%2520"));
    }

    @Test
    void exchangeSendsEncodedFormAndPersistsToken() {
        AtomicReference<StubHttpConnector.Request> captured = new AtomicReference<>();
        var connector = new StubHttpConnector(request -> { captured.set(request); return tokenResponse(); });
        StepVerifier.create(service(connector).exchangeCodeForToken("code+value"))
                .expectNextMatches(token -> token.getAccessToken().equals("new-access")).verifyComplete();
        assertTrue(MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(captured.get().headers().getContentType()));
        assertTrue(captured.get().body().contains("grant_type=authorization_code"));
        assertTrue(captured.get().body().contains("client_secret=secret%26value"));
        assertTrue(captured.get().body().contains("code=code%2Bvalue"));
        verify(storage).saveToken(any(HubSpotTokenResponse.class));
    }

    @Test
    void refreshPreservesRotatedRefreshToken() {
        when(storage.getRefreshToken()).thenReturn(Optional.of("previous"));
        var connector = new StubHttpConnector(request -> {
            assertTrue(request.body().contains("grant_type=refresh_token"));
            assertTrue(request.body().contains("refresh_token=previous"));
            return tokenResponse();
        });
        StepVerifier.create(service(connector).refreshToken())
                .expectNextMatches(token -> token.getRefreshToken().equals("rotated")).verifyComplete();
        verify(storage).saveToken(any(HubSpotTokenResponse.class));
    }

    @Test
    void validTokenDoesNotCallHubSpot() {
        when(storage.getAccessToken()).thenReturn(Optional.of("existing"));
        var connector = new StubHttpConnector(request -> tokenResponse());
        StepVerifier.create(service(connector).getValidAccessToken()).expectNext("existing").verifyComplete();
        assertEquals(0, connector.calls());
    }

    @Test
    void missingAuthorizationReturns401WithoutHttpRequest() {
        when(storage.getAccessToken()).thenReturn(Optional.empty());
        when(storage.getRefreshToken()).thenReturn(Optional.empty());
        var connector = new StubHttpConnector(request -> tokenResponse());
        StepVerifier.create(service(connector).getValidAccessToken())
                .expectErrorMatches(error -> error instanceof HubSpotApiException api
                        && api.getStatusCode().value() == 401).verify();
        assertEquals(0, connector.calls());
    }

    @Test
    void errorResponseDoesNotPersistCredentials() {
        var connector = new StubHttpConnector(request -> {
            var response = new MockClientHttpResponse(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.setBody("{\"message\":\"Invalid code\"}");
            return response;
        });
        StepVerifier.create(service(connector).exchangeCodeForToken("invalid"))
                .expectErrorMatches(error -> error instanceof HubSpotApiException api
                        && api.getStatusCode().value() == 401).verify();
        verify(storage, never()).saveToken(any());
    }
    @Test
    void tokenExchangeRetries429AndPersistsOnlySuccessfulResponse() {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var connector = new StubHttpConnector(request -> {
            if (requests.incrementAndGet() == 1) {
                var limited = new MockClientHttpResponse(HttpStatus.TOO_MANY_REQUESTS);
                limited.getHeaders().set("Retry-After", "0");
                return limited;
            }
            return tokenResponse();
        });
        var config = new HubSpotConfig();
        var oauth = new HubSpotConfig.OAuthConfig();
        oauth.setClientId("client");
        oauth.setClientSecret("secret");
        oauth.setRedirectUri("https://example.com/callback");
        config.setOauth(oauth);
        var service = new HubSpotOAuthServiceImpl(WebClient.builder().baseUrl("https://example.com/token")
                .clientConnector(connector).build(), config, storage,
                br.com.meetime.hubspot.support.RetryFixtures.enabled());
        StepVerifier.create(service.exchangeCodeForToken("code"))
                .expectNextMatches(token -> token.getAccessToken().equals("new-access")).verifyComplete();
        assertEquals(2, requests.get());
        verify(storage, times(1)).saveToken(any(HubSpotTokenResponse.class));
    }
}
