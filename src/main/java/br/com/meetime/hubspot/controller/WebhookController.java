package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.security.WebhookSignatureVerifier;
import br.com.meetime.hubspot.service.WebhookService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {
    private final WebhookSignatureVerifier verifier;
    private final WebhookService service;

    public WebhookController(WebhookSignatureVerifier verifier, WebhookService service) {
        this.verifier = verifier;
        this.service = service;
    }

    @PostMapping(value = "/contacts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> handleWebhook(@RequestBody String body, ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> {
            var request = exchange.getRequest();
            verifier.validateSignatureV3(request.getHeaders().getFirst("X-HubSpot-Signature-v3"),
                    request.getMethod().name(), request.getURI().toString(), body,
                    request.getHeaders().getFirst("X-HubSpot-Request-Timestamp"));
            service.processWebhookEvents(body);
            return ResponseEntity.ok().<Void>build();
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}
