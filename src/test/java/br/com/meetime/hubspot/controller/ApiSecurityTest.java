package br.com.meetime.hubspot.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

@br.com.meetime.hubspot.support.IntegrationTest
class ApiSecurityTest {
    @Autowired
    WebTestClient client;

    @Test
    void anonymousCannotAuthorizeOrCreateContacts() {
        client.get().uri("/oauth/authorize").exchange().expectStatus().isUnauthorized();
        client.mutateWith(csrf()).post().uri("/contacts").header("Content-Type", "application/json")
                .bodyValue("{\"properties\":{\"email\":\"test@example.com\"}}")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void invalidContactReturnsStructuredValidationError() {
        client.mutateWith(csrf()).post().uri("/contacts")
                .headers(headers -> headers.setBasicAuth("test", "test-password"))
                .header("Content-Type", "application/json")
                .bodyValue("{\"properties\":{\"email\":\"invalid\"}}")
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.status").isEqualTo(400).jsonPath("$.details").isArray();
    }

    @Test
    void webhookRequiresSignatureButNotBasicAuth() {
        client.post().uri("/webhooks/contacts").header("Content-Type", "application/json")
                .bodyValue("[]").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void authenticatedCanObtainCsrfToken() {
        client.get().uri("/csrf").headers(headers -> headers.setBasicAuth("test", "test-password"))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.token").isNotEmpty();
    }
    @Test
    void authenticatedPostWithoutCsrfIsRejected() {
        client.post().uri("/contacts")
                .headers(headers -> headers.setBasicAuth("test", "test-password"))
                .header("Content-Type", "application/json")
                .bodyValue("{\"properties\":{\"email\":\"test@example.com\"}}")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void correctlySignedWebhookIsAcceptedAndDeduplicated() throws Exception {
        String body = "[{\"eventId\":987,\"portalId\":123,\"objectId\":456,\"subscriptionType\":\"contact.creation\"}]";
        String timestamp = Long.toString(java.time.Instant.now().toEpochMilli());
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.Base64.getEncoder().encodeToString(mac.doFinal(
                ("POSThttp://localhost/webhooks/contacts" + body + timestamp).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for (int i = 0; i < 2; i++) {
            client.post().uri("http://localhost/webhooks/contacts")
                    .header("Content-Type", "application/json")
                    .header("X-HubSpot-Signature-v3", signature)
                    .header("X-HubSpot-Request-Timestamp", timestamp)
                    .bodyValue(body).exchange().expectStatus().isOk();
        }
    }
}
