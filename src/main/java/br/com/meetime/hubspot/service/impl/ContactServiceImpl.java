package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.domain.request.ContactCreateRequest;
import br.com.meetime.hubspot.domain.response.ContactResponse;
import br.com.meetime.hubspot.client.HubSpotResponseErrors;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.ContactService;
import br.com.meetime.hubspot.service.HubSpotOAuthService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ContactServiceImpl implements ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactServiceImpl.class);
    private static final String CONTACTS_API_PATH = "/crm/v3/objects/contacts";

    private final RateLimiter rateLimiter;
    private final WebClient hubSpotWebClient;
    private final HubSpotOAuthService hubSpotOAuthService;

    public ContactServiceImpl(@Qualifier("hubSpotWebClient") WebClient hubSpotWebClient,
                              HubSpotOAuthService hubSpotOAuthService, RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.hubSpotWebClient = hubSpotWebClient;
        this.hubSpotOAuthService = hubSpotOAuthService;
    }

    public Mono<ContactResponse> createContact(ContactCreateRequest contactRequest) {
        log.info("CONTACT CREATION REQUEST");

        return hubSpotOAuthService.getValidAccessToken()
                .flatMap(accessToken -> {
                    if (!rateLimiter.acquirePermission()) {
                        return Mono.error(RequestNotPermitted.createRequestNotPermitted(rateLimiter));
                    }
                    log.debug("USING ACCESS TOKEN TO CREATE CONTACT");

                    return hubSpotWebClient.post()
                            .uri(CONTACTS_API_PATH)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(contactRequest)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, response -> HubSpotResponseErrors.from(response, "Contact creation"))
                            .bodyToMono(ContactResponse.class)
                            .switchIfEmpty(Mono.error(new HubSpotApiException("Empty contact response", org.springframework.http.HttpStatus.BAD_GATEWAY)))
                            .doOnSuccess(response -> {
                                if (response != null) {
                                    log.info("CONTACT SUCCESSFULLY CREATED IN HUBSPOT. ID: {}", response.getId());
                                } else {
                                    log.warn("CONTACT CREATION RETURNED EMPTY RESPONSE.");
                                }
                            })
                            .doOnError(error -> {
                                if (!(error instanceof HubSpotApiException)) {
                                    log.error("UNEXPECTED ERROR WHILE CREATING CONTACT: {}", error.getMessage(), error);
                                }
                            });
                })
                .doOnError(error -> {
                    if (error instanceof HubSpotApiException hubspotError) {
                        if (hubspotError.getStatusCode().value() == 401) {
                            log.error("FAILED TO CREATE CONTACT: OAUTH AUTHORIZATION REQUIRED OR INVALID.");
                        } else {
                            log.error("FAILED TO CREATE CONTACT: HUBSPOT API ERROR: {}", hubspotError.getMessage());
                        }
                    } else {
                        log.error("FAILED TO CREATE CONTACT: ERROR WHILE FETCHING ACCESS TOKEN: {}", error.getMessage());
                    }
                });
    }

}
