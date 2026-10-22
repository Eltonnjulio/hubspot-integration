package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.client.HubSpotResponseErrors;
import br.com.meetime.hubspot.client.HubSpotRetry;
import br.com.meetime.hubspot.domain.request.ContactCreateRequest;
import br.com.meetime.hubspot.domain.response.ContactResponse;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.ContactService;
import br.com.meetime.hubspot.service.HubSpotOAuthService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ContactServiceImpl implements ContactService {
    private final WebClient client;
    private final HubSpotOAuthService oauth;
    private final RateLimiter limiter;
    private final HubSpotRetry retry;

    public ContactServiceImpl(@Qualifier("hubSpotWebClient") WebClient client,
            HubSpotOAuthService oauth, RateLimiter limiter, HubSpotRetry retry) {
        this.client = client;
        this.oauth = oauth;
        this.limiter = limiter;
        this.retry = retry;
    }

    @Override
    public Mono<ContactResponse> createContact(ContactCreateRequest request) {
        return Mono.defer(() -> oauth.getValidAccessToken().flatMap(token -> {
            if (!limiter.acquirePermission()) {
                return Mono.error(RequestNotPermitted.createRequestNotPermitted(limiter));
            }
            return client.post().uri("/crm/v3/objects/contacts")
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> HubSpotResponseErrors.from(response, "Contact creation"))
                    .bodyToMono(ContactResponse.class)
                    .switchIfEmpty(Mono.error(new HubSpotApiException("Empty contact response", HttpStatus.BAD_GATEWAY)));
        })).retryWhen(retry.policy());
    }
}
