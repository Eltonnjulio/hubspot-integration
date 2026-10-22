package br.com.meetime.hubspot.security;

import br.com.meetime.hubspot.config.HubSpotConfig;
import br.com.meetime.hubspot.exception.WebhookAuthenticationException;
import br.com.meetime.hubspot.security.impl.WebhookSignatureVerifierImpl;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class WebhookSignatureVerifierTest {
    private WebhookSignatureVerifier verifier() {
        var config = new HubSpotConfig();
        var webhook = new HubSpotConfig.WebhookConfig();
        webhook.setClientSecret("test-secret");
        config.setWebhook(webhook);
        return new WebhookSignatureVerifierImpl(config);
    }

    private String sign(String body, String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(
                ("POSThttps://example.com/webhook" + body + timestamp).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsSignedRequestAndRejectsTampering() throws Exception {
        String timestamp = Long.toString(Instant.now().toEpochMilli());
        String signature = sign("[]", timestamp);
        assertDoesNotThrow(() -> verifier().validateSignatureV3(signature, "POST", "https://example.com/webhook", "[]", timestamp));
        assertThrows(WebhookAuthenticationException.class, () -> verifier().validateSignatureV3(signature, "POST", "https://example.com/webhook", "{}", timestamp));
        assertThrows(WebhookAuthenticationException.class, () -> verifier().validateSignatureV3(signature, "GET", "https://example.com/webhook", "[]", timestamp));
    }

    @Test
    void rejectsExpiredAndMalformedTimestamps() throws Exception {
        String timestamp = Long.toString(Instant.now().minusSeconds(301).toEpochMilli());
        String signature = sign("[]", timestamp);
        assertThrows(WebhookAuthenticationException.class, () -> verifier().validateSignatureV3(signature, "POST", "https://example.com/webhook", "[]", timestamp));
        assertThrows(WebhookAuthenticationException.class, () -> verifier().validateSignatureV3(signature, "POST", "https://example.com/webhook", "[]", "invalid"));
    }
}
