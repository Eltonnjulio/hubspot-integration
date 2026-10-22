package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.service.HubSpotOAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.RedirectView;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Controller
@RequestMapping("/oauth")
public class OAuthController {
    private final HubSpotOAuthService service;

    public OAuthController(HubSpotOAuthService service) { this.service = service; }

    @GetMapping("/authorize")
    public RedirectView redirectToHubSpotAuthorization(WebSession session) {
        String state = UUID.randomUUID().toString();
        session.getAttributes().put("oauthState", state);
        session.getAttributes().put("oauthStateExpiry", Instant.now().plusSeconds(600));
        return new RedirectView(UriComponentsBuilder.fromUriString(service.getAuthorizationUrl())
                .queryParam("state", state).build(true).toUriString());
    }

    @GetMapping("/callback")
    public Mono<ResponseEntity<String>> handleCallback(@RequestParam("code") String code,
            @RequestParam("state") String state, WebSession session) {
        String expected = (String) session.getAttributes().remove("oauthState");
        Instant expiry = (Instant) session.getAttributes().remove("oauthStateExpiry");
        if (expected == null || expiry == null || Instant.now().isAfter(expiry)
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OAuth state"));
        }
        return service.exchangeCodeForToken(code)
                .map(token -> ResponseEntity.ok("OAuth authorization completed successfully!"));
    }
}
