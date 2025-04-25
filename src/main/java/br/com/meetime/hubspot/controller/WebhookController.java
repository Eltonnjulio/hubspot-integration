package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.security.WebhookSignatureVerifier;
import br.com.meetime.hubspot.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookService webhookService;

    public WebhookController(WebhookSignatureVerifier signatureVerifier, WebhookService webhookService) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/contacts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> handleContactWebhook(ServerWebExchange exchange) {
        return exchange.getRequest().getBody()
                .reduce(new StringBuilder(), (builder, dataBuffer) -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    return builder.append(new String(bytes, StandardCharsets.UTF_8));
                })
                .flatMap(body -> {
                    String signature = exchange.getRequest().getHeaders().getFirst("X-HubSpot-Signature-v3");
                    String timestamp = exchange.getRequest().getHeaders().getFirst("X-HubSpot-Request-Timestamp");
                    String method = exchange.getRequest().getMethod().name();
                    String uri = exchange.getRequest().getURI().getPath();

                    log.info("Recebido evento de webhook do HubSpot");
                    log.debug("URI: {}", uri);
                    log.debug("Método: {}", method);
                    log.debug("Corpo: {}", body);
                    log.debug("Timestamp: {}", timestamp);
                    log.debug("Assinatura (v3): {}", signature);

                    signatureVerifier.validateSignatureV3(signature, method, uri, String.valueOf(body), timestamp);
                    webhookService.processWebhookEvents(String.valueOf(body));

                    return Mono.empty();
                });
    }
}
