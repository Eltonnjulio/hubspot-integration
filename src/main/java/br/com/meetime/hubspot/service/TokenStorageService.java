package br.com.meetime.hubspot.service;

import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;

import java.util.Optional;

public interface TokenStorageService {

    public void saveToken(HubSpotTokenResponse tokenResponse);
    public Optional<String> getAccessToken();

    public Optional<String> getRefreshToken();

    public void clearToken();

    public boolean hasValidToken();
}