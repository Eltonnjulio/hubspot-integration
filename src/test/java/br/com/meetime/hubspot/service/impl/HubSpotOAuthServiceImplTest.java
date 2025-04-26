package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.config.HubSpotConfig;
import br.com.meetime.hubspot.domain.dto.HubSpotErrorDTO;
import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.TokenStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class HubSpotOAuthServiceImplTest {

    @Mock
    private WebClient hubSpotOAuthWebClient;
    @Mock
    private HubSpotConfig hubSpotConfig;
    @Mock
    private TokenStorageService tokenStorageService;

    @InjectMocks
    private HubSpotOAuthServiceImpl hubSpotOAuthService;

    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;


    @Captor ArgumentCaptor<HubSpotTokenResponse> tokenCaptor;

    private HubSpotConfig.OAuthConfig oauthConfig;

    @BeforeEach
    void setUp() {
        oauthConfig = new HubSpotConfig.OAuthConfig();
        oauthConfig.setClientId("test-client-id");
        oauthConfig.setClientSecret("test-client-secret");
        oauthConfig.setRedirectUri("http://localhost/callback");
        oauthConfig.setAuthorizationUri("https://auth.uri");
        oauthConfig.setTokenUri("/oauth/token");
        oauthConfig.setScopes("scope1 scope2");

        when(hubSpotConfig.getOauth()).thenReturn(oauthConfig);
        when(hubSpotConfig.getFullAuthorizationUri()).thenReturn(oauthConfig.getAuthorizationUri());

        when(hubSpotOAuthWebClient.post()).thenReturn(requestBodyUriSpec);
        // Use any() para o BodyInserter
        when(requestBodyUriSpec.body(any(BodyInserter.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("getAuthorizationUrl should build correct url")
    void getAuthorizationUrl_shouldBuildCorrectUrl() {
        String url = hubSpotOAuthService.getAuthorizationUrl();

        assertTrue(url.startsWith(oauthConfig.getAuthorizationUri()));
        assertTrue(url.contains("client_id=" + oauthConfig.getClientId()));
        assertTrue(url.contains("redirect_uri=" + oauthConfig.getRedirectUri()));
        assertTrue(url.contains("scope=" + oauthConfig.getEncodedScopes()));
    }

    @Test
    @DisplayName("exchangeCodeForToken should succeed and save token")
    void exchangeCodeForToken_shouldSucceedAndSaveToken() {
        String authCode = "valid-auth-code";
        HubSpotTokenResponse mockTokenResponse = new HubSpotTokenResponse();
        mockTokenResponse.setAccessToken("new-access-token");
        mockTokenResponse.setRefreshToken("new-refresh-token");
        mockTokenResponse.setExpiresIn(3600);

        when(responseSpec.bodyToMono(HubSpotTokenResponse.class)).thenReturn(Mono.just(mockTokenResponse));

        Mono<HubSpotTokenResponse> result = hubSpotOAuthService.exchangeCodeForToken(authCode);

        StepVerifier.create(result)
                .expectNextMatches(token ->
                        "new-access-token".equals(token.getAccessToken()) &&
                                "new-refresh-token".equals(token.getRefreshToken()))
                .verifyComplete();

        verify(requestBodyUriSpec).body(any(BodyInserter.class));
        verify(tokenStorageService).saveToken(mockTokenResponse);
    }

    @Test
    @DisplayName("exchangeCodeForToken should return error when API fails")
    void exchangeCodeForToken_shouldReturnError_whenApiFails() {
        String authCode = "invalid-auth-code";
        HubSpotErrorDTO errorDTO = new HubSpotErrorDTO();
        errorDTO.setMessage("Invalid code");
        HubSpotApiException expectedException = new HubSpotApiException(
                "Failed to obtain access token. Status: 400", HttpStatus.BAD_REQUEST, errorDTO);

        when(responseSpec.bodyToMono(HubSpotTokenResponse.class)).thenReturn(Mono.error(expectedException));

        Mono<HubSpotTokenResponse> result = hubSpotOAuthService.exchangeCodeForToken(authCode);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof HubSpotApiException &&
                                ((HubSpotApiException) throwable).getStatusCode() == HttpStatus.BAD_REQUEST &&
                                ((HubSpotApiException) throwable).getErrorBody() != null &&
                                "Invalid code".equals(((HubSpotApiException) throwable).getErrorBody().getMessage())
                )
                .verify();

        verify(tokenStorageService, never()).saveToken(any());
    }


    @Test
    @DisplayName("refreshToken should succeed and save token")
    void refreshToken_shouldSucceedAndSaveToken() {
        String existingRefreshToken = "old-refresh-token";
        when(tokenStorageService.getRefreshToken()).thenReturn(Optional.of(existingRefreshToken));

        HubSpotTokenResponse mockTokenResponse = new HubSpotTokenResponse();
        mockTokenResponse.setAccessToken("refreshed-access-token");
        mockTokenResponse.setExpiresIn(1800);

        when(responseSpec.bodyToMono(HubSpotTokenResponse.class)).thenReturn(Mono.just(mockTokenResponse));

        Mono<HubSpotTokenResponse> result = hubSpotOAuthService.refreshToken();

        StepVerifier.create(result)
                .expectNextMatches(token ->
                        "refreshed-access-token".equals(token.getAccessToken()) &&
                                existingRefreshToken.equals(token.getRefreshToken()))
                .verifyComplete();

        verify(requestBodyUriSpec).body(any(BodyInserter.class));
        verify(tokenStorageService).saveToken(tokenCaptor.capture());
        assertEquals("refreshed-access-token", tokenCaptor.getValue().getAccessToken());
        assertEquals(existingRefreshToken, tokenCaptor.getValue().getRefreshToken());
    }

    @Test
    @DisplayName("refreshToken should return error when API fails")
    void refreshToken_shouldReturnError_whenApiFails() {
        String existingRefreshToken = "invalid-refresh-token";
        when(tokenStorageService.getRefreshToken()).thenReturn(Optional.of(existingRefreshToken));

        HubSpotErrorDTO errorDTO = new HubSpotErrorDTO();
        errorDTO.setMessage("Token expired");
        HubSpotApiException expectedException = new HubSpotApiException(
                "Failed to refresh token. Status: 401", HttpStatus.UNAUTHORIZED, errorDTO);

        when(responseSpec.bodyToMono(HubSpotTokenResponse.class)).thenReturn(Mono.error(expectedException));

        Mono<HubSpotTokenResponse> result = hubSpotOAuthService.refreshToken();

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof HubSpotApiException &&
                                ((HubSpotApiException) throwable).getStatusCode() == HttpStatus.UNAUTHORIZED
                )
                .verify();

        verify(tokenStorageService, never()).saveToken(any());
    }


    @Test
    @DisplayName("getValidAccessToken should return existing token when valid")
    void getValidAccessToken_shouldReturnExistingToken_whenValid() {
        String validToken = "valid-token";
        when(tokenStorageService.getAccessToken()).thenReturn(Optional.of(validToken));

        Mono<String> result = hubSpotOAuthService.getValidAccessToken();

        StepVerifier.create(result)
                .expectNext(validToken)
                .verifyComplete();

        verify(tokenStorageService, never()).getRefreshToken();
        verify(hubSpotOAuthWebClient, never()).post();
    }

    @Test
    @DisplayName("getValidAccessToken should refresh token when existing invalid and refresh available")
    void getValidAccessToken_shouldRefreshToken_whenExistingInvalidAndRefreshAvailable() {
        String existingRefreshToken = "old-refresh-token";
        when(tokenStorageService.getAccessToken()).thenReturn(Optional.empty());
        when(tokenStorageService.getRefreshToken()).thenReturn(Optional.of(existingRefreshToken));

        HubSpotTokenResponse refreshedTokenResponse = new HubSpotTokenResponse();
        refreshedTokenResponse.setAccessToken("refreshed-access-token");
        refreshedTokenResponse.setExpiresIn(1800);

        when(responseSpec.bodyToMono(HubSpotTokenResponse.class)).thenReturn(Mono.just(refreshedTokenResponse));

        Mono<String> result = hubSpotOAuthService.getValidAccessToken();

        StepVerifier.create(result)
                .expectNext("refreshed-access-token")
                .verifyComplete();

        verify(hubSpotOAuthWebClient).post();
        verify(tokenStorageService).saveToken(any(HubSpotTokenResponse.class));
    }

}