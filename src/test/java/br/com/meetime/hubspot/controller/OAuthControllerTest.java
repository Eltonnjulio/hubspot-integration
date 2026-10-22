package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.service.HubSpotOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockWebSession;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthControllerTest {
    private final HubSpotOAuthService service = mock(HubSpotOAuthService.class);
    private final OAuthController controller = new OAuthController(service);

    @Test
    void authorizationBindsStateToSession() {
        when(service.getAuthorizationUrl()).thenReturn("https://example.com/oauth?client_id=test");
        var session = new MockWebSession();
        String url = controller.redirectToHubSpotAuthorization(session).getUrl();
        assertEquals(session.getAttribute("oauthState"),
                UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state"));
    }

    @Test
    void callbackConsumesStateOnce() {
        var session = new MockWebSession();
        session.getAttributes().put("oauthState", "valid");
        session.getAttributes().put("oauthStateExpiry", Instant.now().plusSeconds(60));
        when(service.exchangeCodeForToken("code")).thenReturn(Mono.just(new HubSpotTokenResponse()));
        StepVerifier.create(controller.handleCallback("code", "valid", session))
                .expectNextMatches(response -> response.getStatusCode().is2xxSuccessful()).verifyComplete();
        StepVerifier.create(controller.handleCallback("code", "valid", session))
                .expectError(ResponseStatusException.class).verify();
        verify(service, times(1)).exchangeCodeForToken("code");
    }

    @Test
    void invalidAndExpiredStateNeverExchangeCode() {
        var session = new MockWebSession();
        session.getAttributes().put("oauthState", "valid");
        session.getAttributes().put("oauthStateExpiry", Instant.now().minusSeconds(1));
        StepVerifier.create(controller.handleCallback("code", "valid", session))
                .expectError(ResponseStatusException.class).verify();
        StepVerifier.create(controller.handleCallback("code", "invalid", new MockWebSession()))
                .expectError(ResponseStatusException.class).verify();
        verifyNoInteractions(service);
    }
    @Test
    void mismatchedStateInExistingSessionNeverExchangesCode() {
        var session = new MockWebSession();
        session.getAttributes().put("oauthState", "expected");
        session.getAttributes().put("oauthStateExpiry", Instant.now().plusSeconds(60));
        StepVerifier.create(controller.handleCallback("code", "attacker", session))
                .expectError(ResponseStatusException.class).verify();
        verifyNoInteractions(service);
    }
}
