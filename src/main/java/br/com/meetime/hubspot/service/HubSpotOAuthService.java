package br.com.meetime.hubspot.service;

import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import reactor.core.publisher.Mono;

public interface HubSpotOAuthService {

    public String getAuthorizationUrl();

    public Mono<HubSpotTokenResponse> exchangeCodeForToken(String code);

    public Mono<HubSpotTokenResponse> refreshToken();

    public Mono<String> getValidAccessToken();

}
