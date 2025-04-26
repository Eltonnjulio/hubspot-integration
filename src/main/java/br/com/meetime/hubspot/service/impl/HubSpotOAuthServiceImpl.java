package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.config.HubSpotConfig;
import br.com.meetime.hubspot.domain.dto.HubSpotErrorDTO;
import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.HubSpotOAuthService;
import br.com.meetime.hubspot.service.TokenStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
public class HubSpotOAuthServiceImpl implements HubSpotOAuthService {

    private static final Logger log = LoggerFactory.getLogger(HubSpotOAuthServiceImpl.class);

    private final WebClient hubSpotOAuthWebClient;
    private final HubSpotConfig hubSpotConfig;
    private final TokenStorageService tokenStorageService;

    public HubSpotOAuthServiceImpl(@Qualifier("hubSpotOAuthWebClient") WebClient hubSpotOAuthWebClient,
                                   HubSpotConfig hubSpotConfig,
                                   TokenStorageService tokenStorageService) {
        this.hubSpotOAuthWebClient = hubSpotOAuthWebClient;
        this.hubSpotConfig = hubSpotConfig;
        this.tokenStorageService = tokenStorageService;
    }

    public String getAuthorizationUrl() {
        String url = UriComponentsBuilder.fromHttpUrl(hubSpotConfig.getFullAuthorizationUri())
                .queryParam("client_id", hubSpotConfig.getOauth().getClientId())
                .queryParam("redirect_uri", hubSpotConfig.getOauth().getRedirectUri())
                .queryParam("scope", hubSpotConfig.getOauth().getEncodedScopes())
                .build()
                .toUriString();
        log.debug("GENERATED HUBSPOT AUTHORIZATION URL: {}", url);
        return url;
    }

    public Mono<HubSpotTokenResponse> exchangeCodeForToken(String code) {
        log.info("EXCHANGING AUTHORIZATION CODE FOR ACCESS TOKEN...");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("redirect_uri", hubSpotConfig.getOauth().getRedirectUri());
        formData.add("client_id", hubSpotConfig.getOauth().getClientId());
        formData.add("client_secret", hubSpotConfig.getOauth().getClientSecret());

        return hubSpotOAuthWebClient.post()
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(HubSpotErrorDTO.class)
                                .flatMap(errorBody -> Mono.<Throwable>error(buildTokenError(response, errorBody)))
                                .switchIfEmpty(Mono.<Throwable>error(buildTokenError(response)))
                )

                .bodyToMono(HubSpotTokenResponse.class)
                .doOnSuccess(token -> {
                    log.info("ACCESS TOKEN RECEIVED SUCCESSFULLY.");
                    tokenStorageService.saveToken(token);
                })
                .doOnError(error -> log.error("ERROR WHILE EXCHANGING CODE FOR TOKEN: {}", error.getMessage()));
    }

    public Mono<HubSpotTokenResponse> refreshToken() {
        log.info("ATTEMPTING TO REFRESH ACCESS TOKEN...");

        String refreshToken = tokenStorageService.getRefreshToken()
                .orElseThrow(() -> new IllegalStateException("NO REFRESH TOKEN AVAILABLE FOR RENEWAL."));

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        formData.add("client_id", hubSpotConfig.getOauth().getClientId());
        formData.add("client_secret", hubSpotConfig.getOauth().getClientSecret());

        return hubSpotOAuthWebClient.post()
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(HubSpotErrorDTO.class)
                                .<Throwable>flatMap(errorBody -> Mono.error(buildRefreshError(response, errorBody)))
                                .switchIfEmpty(Mono.<Throwable>error(buildRefreshError(response)))
                )

                .bodyToMono(HubSpotTokenResponse.class)
                .doOnSuccess(newToken -> {
                    log.info("ACCESS TOKEN REFRESHED SUCCESSFULLY.");
                    newToken.setRefreshToken(refreshToken);
                    tokenStorageService.saveToken(newToken);
                })
                .doOnError(error -> log.error("ERROR DURING TOKEN REFRESH: {}", error.getMessage()));
    }

    public Mono<String> getValidAccessToken() {
        return Mono.defer(() -> {
            Optional<String> currentToken = tokenStorageService.getAccessToken();
            if (currentToken.isPresent()) {
                log.debug("USING EXISTING ACCESS TOKEN FROM MEMORY.");
                return Mono.just(currentToken.get());
            } else if (tokenStorageService.getRefreshToken().isPresent()) {
                log.info("ACCESS TOKEN EXPIRED OR MISSING. TRYING TO REFRESH...");
                return refreshToken()
                        .map(HubSpotTokenResponse::getAccessToken)
                        .doOnError(err -> log.error("FAILED TO REFRESH TOKEN. REAUTHENTICATION MAY BE REQUIRED.", err));
            } else {
                log.warn("NO ACCESS OR REFRESH TOKEN FOUND. AUTHORIZATION REQUIRED.");
                return Mono.error(new HubSpotApiException("OAUTH AUTHORIZATION REQUIRED.", HttpStatusCode.valueOf(401)));
            }
        });
    }

    private HubSpotApiException buildTokenError(ClientResponse response, HubSpotErrorDTO body) {
        String message = String.format("FAILED TO OBTAIN ACCESS TOKEN: %d", response.statusCode().value());
        return new HubSpotApiException(message, response.statusCode(), body);
    }

    private HubSpotApiException buildTokenError(ClientResponse response) {
        String message = String.format("FAILED TO OBTAIN ACCESS TOKEN: %d (NO ERROR BODY)", response.statusCode().value());
        return new HubSpotApiException(message, response.statusCode());
    }

    private HubSpotApiException buildRefreshError(ClientResponse response, HubSpotErrorDTO body) {
        String message = String.format("FAILED TO REFRESH TOKEN: %d", response.statusCode().value());
        return new HubSpotApiException(message, response.statusCode(), body);
    }

    private HubSpotApiException buildRefreshError(ClientResponse response) {
        String message = String.format("FAILED TO REFRESH TOKEN: %d (NO ERROR BODY)", response.statusCode().value());
        return new HubSpotApiException(message, response.statusCode());
    }
}
