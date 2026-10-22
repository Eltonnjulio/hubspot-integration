package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.service.TokenStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TokenStorageServiceImplTest {

    private TokenStorageService tokenStorageService;

    @BeforeEach
    void setUp() {
        tokenStorageService = new TokenStorageServiceImpl(new br.com.meetime.hubspot.support.InMemoryTokenRepository(), java.time.Clock.systemUTC());
    }

    private HubSpotTokenResponse createTokenResponse(String accessToken, String refreshToken, long expiresIn) {
        HubSpotTokenResponse token = new HubSpotTokenResponse();
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setExpiresIn(expiresIn);
        token.setTokenType("bearer");
        return token;
    }

    @Test
    @DisplayName("saveAndGetAccessToken should return valid token")
    void saveAndGetAccessToken_shouldReturnValidToken() {
        HubSpotTokenResponse token = createTokenResponse("access123", "refresh456", 3600);

        tokenStorageService.saveToken(token);
        Optional<String> accessToken = tokenStorageService.getAccessToken();

        assertTrue(accessToken.isPresent());
        assertEquals("access123", accessToken.get());
        assertTrue(tokenStorageService.hasValidToken());
    }


    @Test
    @DisplayName("saveAndGetRefreshToken should return token")
    void saveAndGetRefreshToken_shouldReturnToken() {
        HubSpotTokenResponse token = createTokenResponse("access123", "refresh456", 3600);

        tokenStorageService.saveToken(token);
        Optional<String> refreshToken = tokenStorageService.getRefreshToken();

        assertTrue(refreshToken.isPresent());
        assertEquals("refresh456", refreshToken.get());
    }

    @Test
    @DisplayName("getTokens should return empty when no token saved")
    void getTokens_shouldReturnEmpty_whenNoTokenSaved() {
        Optional<String> accessToken = tokenStorageService.getAccessToken();
        Optional<String> refreshToken = tokenStorageService.getRefreshToken();

        assertTrue(accessToken.isEmpty());
        assertTrue(refreshToken.isEmpty());
        assertFalse(tokenStorageService.hasValidToken());
    }

    @Test
    @DisplayName("clearToken should remove stored token")
    void clearToken_shouldRemoveStoredToken() {
        HubSpotTokenResponse token = createTokenResponse("access123", "refresh456", 3600);
        tokenStorageService.saveToken(token);
        assertTrue(tokenStorageService.getAccessToken().isPresent());

        tokenStorageService.clearToken();
        Optional<String> accessToken = tokenStorageService.getAccessToken();
        Optional<String> refreshToken = tokenStorageService.getRefreshToken();

        assertTrue(accessToken.isEmpty());
        assertTrue(refreshToken.isEmpty());
        assertFalse(tokenStorageService.hasValidToken());
    }

    @Test
    @DisplayName("saveToken should replace existing token")
    void saveToken_shouldReplaceExistingToken() {
        HubSpotTokenResponse token1 = createTokenResponse("access1", "refresh1", 100);
        HubSpotTokenResponse token2 = createTokenResponse("access2", "refresh2", 200);
        tokenStorageService.saveToken(token1);

        tokenStorageService.saveToken(token2);
        Optional<String> accessToken = tokenStorageService.getAccessToken();
        Optional<String> refreshToken = tokenStorageService.getRefreshToken();

        assertTrue(accessToken.isPresent());
        assertEquals("access2", accessToken.get());
        assertTrue(refreshToken.isPresent());
        assertEquals("refresh2", refreshToken.get());
    }

    @Test
    void expiredTokenPreservesRefreshTokenWithoutBlocking() {
        var clock = org.mockito.Mockito.mock(java.time.Clock.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        tokenStorageService = new TokenStorageServiceImpl(new br.com.meetime.hubspot.support.InMemoryTokenRepository(), clock);
        tokenStorageService.saveToken(createTokenResponse("expired", "refresh", 1));
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(2));
        assertTrue(tokenStorageService.getAccessToken().isEmpty());
        assertEquals("refresh", tokenStorageService.getRefreshToken().orElseThrow());
    }

    @Test
    void storedTokenCannotBeChangedByCaller() {
        var token = createTokenResponse("original", "refresh", 3600);
        tokenStorageService.saveToken(token);
        token.setAccessToken("changed");
        assertEquals("original", tokenStorageService.getAccessToken().orElseThrow());
    }

}