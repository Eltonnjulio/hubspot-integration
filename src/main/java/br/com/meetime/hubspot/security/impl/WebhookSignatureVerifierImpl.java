package br.com.meetime.hubspot.security.impl;

import br.com.meetime.hubspot.config.HubSpotConfig;
import br.com.meetime.hubspot.exception.WebhookAuthenticationException;
import br.com.meetime.hubspot.security.WebhookSignatureVerifier;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class WebhookSignatureVerifierImpl implements WebhookSignatureVerifier {
    private static final Pattern ENCODED = Pattern.compile("%(?:3A|2F|3F|40|21|24|27|28|29|2A|2C|3B)", Pattern.CASE_INSENSITIVE);
    private final byte[] secret;

    public WebhookSignatureVerifierImpl(HubSpotConfig config) {
        String value = Objects.requireNonNull(config.getWebhook().getClientSecret());
        if (value.isBlank()) { throw new IllegalArgumentException("Webhook secret must not be blank"); }
        secret = value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void validateSignatureV3(String signature, String method, String uri, String body, String timestamp) {
        if (signature == null || signature.isBlank() || timestamp == null || body == null) {
            throw new WebhookAuthenticationException("Missing webhook signature data");
        }
        try {
            long age = Instant.now().toEpochMilli() - Long.parseLong(timestamp);
            if (age < -30_000 || age > 300_000) {
                throw new WebhookAuthenticationException("Webhook timestamp outside allowed window");
            }
            String decodedUri = ENCODED.matcher(uri).replaceAll(match ->
                    java.util.regex.Matcher.quoteReplacement(Character.toString(
                            (char) Integer.parseInt(match.group().substring(1), 16))));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal((method + decodedUri + body + timestamp).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, Base64.getDecoder().decode(signature))) {
                throw new WebhookAuthenticationException("Invalid webhook signature");
            }
        } catch (WebhookAuthenticationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new WebhookAuthenticationException("Malformed webhook signature data", e);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Unable to calculate webhook signature", e);
        }
    }
}
