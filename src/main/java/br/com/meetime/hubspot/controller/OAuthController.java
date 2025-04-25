package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.service.HubSpotOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.RedirectView;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/oauth")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);
    private final HubSpotOAuthService hubSpotOAuthService;

    public OAuthController(HubSpotOAuthService hubSpotOAuthService) {
        this.hubSpotOAuthService = hubSpotOAuthService;
    }

    @GetMapping("/authorize")
    public RedirectView redirectToHubSpotAuthorization() {
        String authorizationUrl = hubSpotOAuthService.getAuthorizationUrl();
        log.info("REDIRECTING USER TO HUBSPOT AUTHORIZATION PAGE: {}", authorizationUrl);
        return new RedirectView(authorizationUrl);
    }

    @GetMapping("/callback")
    public Mono<ResponseEntity<String>> handleCallback(@RequestParam("code") String code) {
        log.info("RECEIVED HUBSPOT CALLBACK WITH AUTHORIZATION CODE.");
        return hubSpotOAuthService.exchangeCodeForToken(code)
                .map(tokenResponse -> ResponseEntity.ok("OAuth authorization completed successfully! Access token received."))
                .onErrorResume(e -> {
                    log.error("FAILED TO PROCESS HUBSPOT CALLBACK: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to complete OAuth authorization: " + e.getMessage()));
                });
    }
}
