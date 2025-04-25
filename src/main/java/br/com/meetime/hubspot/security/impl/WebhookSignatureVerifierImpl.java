package br.com.meetime.hubspot.security.impl
        ;

import br.com.meetime.hubspot.security.WebhookSignatureVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class WebhookSignatureVerifierImpl implements WebhookSignatureVerifier {

    @Value("${webhook.client-secret")
    private String clientSecret;

    private static final long MAX_ALLOWED_TIMESTAMP = 300_000; // 5 minutos em milissegundos

    @Override
    public void validateSignatureV3(String signature, String method, String uri, String body, String timestamp) {
        long requestTimestamp = Long.parseLong(timestamp);
        long currentTime = System.currentTimeMillis();

        if (Math.abs(currentTime - requestTimestamp) > MAX_ALLOWED_TIMESTAMP) {
            throw new SecurityException("Timestamp inválido: possível replay attack.");
        }

        String rawString = method + uri + body + timestamp;
        String computedSignature = computeHmac(rawString);

        if (!constantTimeEquals(computedSignature, signature)) {
            throw new SecurityException("Assinatura inválida: a requisição pode não ser do HubSpot.");
        }
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao computar HMAC: " + e.getMessage(), e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
