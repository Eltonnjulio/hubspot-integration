package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.exception.WebhookAuthenticationException;
import br.com.meetime.hubspot.security.WebhookSignatureVerifier;
import br.com.meetime.hubspot.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookController.class);
    private static final String SIGNATURE_HEADER_V3 = "X-HubSpot-Signature-v3";

    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookService webhookService;

    public WebhookController(WebhookSignatureVerifier signatureVerifier, WebhookService webhookService) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/contacts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> handleWebhook(ServerWebExchange exchange) {
        if (LOG.isTraceEnabled()) {
            String headersString = exchange.getRequest().getHeaders().entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + String.join(",", entry.getValue()))
                    .collect(Collectors.joining("\n  "));
            LOG.trace("RECEIVED WEBHOOK HEADERS:\n  {}", headersString);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    String body = new String(bytes, StandardCharsets.UTF_8);
                    DataBufferUtils.release(dataBuffer);

                    LOG.debug("CAPTURED WEBHOOK BODY (UTF-8) FOR VALIDATION: [{}]", body);

                    return processWebhookRequestV3(exchange, body);
                })
                .onErrorResume(e -> {
                    if (e instanceof WebhookAuthenticationException) {
                        LOG.warn("WEBHOOK V3 VALIDATION FAILED: {}", e.getMessage().toUpperCase());
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }
                    LOG.error("UNEXPECTED ERROR PROCESSING WEBHOOK: {}", e.getMessage().toUpperCase(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    private Mono<ResponseEntity<Void>> processWebhookRequestV3(ServerWebExchange exchange, String body) {
        String signatureV3 = exchange.getRequest().getHeaders().getFirst(SIGNATURE_HEADER_V3);
        String requestUri = exchange.getRequest().getURI().toString();

        if (signatureV3 == null || signatureV3.isBlank()) {
            LOG.warn("WEBHOOK RECEIVED WITHOUT {} HEADER FOR URI {}", SIGNATURE_HEADER_V3, requestUri);
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        }

        LOG.info("PROCESSING WEBHOOK V3 FOR URI: {}", requestUri);
        LOG.debug(">>> STARTING V3 SIGNATURE VALIDATION <<<");
        LOG.debug("URI: {}", requestUri);
        LOG.debug("RECEIVED SIGNATURE (X-HUBSPOT-SIGNATURE-V3): [{}]", signatureV3);
        LOG.debug("REQUEST BODY FOR VALIDATION (LENGTH {}): [{}]", body.length(), body);

        try {
            signatureVerifier.validateSignatureV3(signatureV3, body);

            LOG.info(">>> V3 SIGNATURE VALIDATED SUCCESSFULLY <<< FOR URI: {}", requestUri);

            LOG.debug("STARTING WEBHOOK BODY PROCESSING BY SERVICE LAYER...");
            webhookService.processWebhookEvents(body);

            LOG.info("WEBHOOK PROCESSED SUCCESSFULLY BY SERVICE LAYER FOR URI: {}", requestUri);
            return Mono.just(ResponseEntity.ok().build());

        } catch (WebhookAuthenticationException e) {
            LOG.warn(">>> V3 SIGNATURE VALIDATION FAILED <<< FOR URI {}: {}", requestUri, e.getMessage().toUpperCase());
            return Mono.error(e);
        } catch (Exception e) {
            LOG.error("UNEXPECTED ERROR PROCESSING WEBHOOK BODY OR IN SERVICE LOGIC FOR URI {}: {}", requestUri, e.getMessage().toUpperCase(), e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        }
    }
}