package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.HubSpotOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.result.view.RedirectView;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    @Mock
    private HubSpotOAuthService hubSpotOAuthService;

    @InjectMocks
    private OAuthController oAuthController;

    private final String testAuthCode = "test-code";
    private final String testAuthUrl = "http://hubspot.com/auth?client_id=123";

    @BeforeEach
    void setUp() {

    }

    @Test
    @DisplayName("redirectToHubSpotAuthorization deve retornar RedirectView com URL correta")
    void redirectToHubSpotAuthorization_shouldReturnRedirectView() {

        when(hubSpotOAuthService.getAuthorizationUrl()).thenReturn(testAuthUrl);

        RedirectView redirectView = oAuthController.redirectToHubSpotAuthorization();

        assertNotNull(redirectView);
        assertEquals(testAuthUrl, redirectView.getUrl());

    }

    @Test
    @DisplayName("handleCallback deve retornar OK quando troca de código for bem sucedida")
    void handleCallback_shouldReturnOk_whenExchangeSucceeds() {

        HubSpotTokenResponse mockToken = new HubSpotTokenResponse();
        mockToken.setAccessToken("mock-token");
        when(hubSpotOAuthService.exchangeCodeForToken(anyString()))
                .thenReturn(Mono.just(mockToken));


        Mono<ResponseEntity<String>> result = oAuthController.handleCallback(testAuthCode);


        StepVerifier.create(result)
                .expectNextMatches(responseEntity ->
                        responseEntity.getStatusCode() == HttpStatus.OK &&
                                responseEntity.getBody() != null &&
                                responseEntity.getBody().contains("completed successfully")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("handleCallback deve retornar INTERNAL_SERVER_ERROR quando troca de código falhar")
    void handleCallback_shouldReturnInternalServerError_whenExchangeFails() {

        HubSpotApiException apiException = new HubSpotApiException("Exchange failed", HttpStatus.BAD_REQUEST);
        when(hubSpotOAuthService.exchangeCodeForToken(anyString()))
                .thenReturn(Mono.error(apiException));


        Mono<ResponseEntity<String>> result = oAuthController.handleCallback(testAuthCode);


        StepVerifier.create(result)
                .expectNextMatches(responseEntity ->
                        responseEntity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR &&
                                responseEntity.getBody() != null &&
                                responseEntity.getBody().contains("Failed to complete OAuth") &&
                                responseEntity.getBody().contains(apiException.getMessage())
                )
                .verifyComplete();
    }
}