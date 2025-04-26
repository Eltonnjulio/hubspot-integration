package br.com.meetime.hubspot.security.impl;

import br.com.meetime.hubspot.config.HubSpotConfig;
import br.com.meetime.hubspot.exception.WebhookAuthenticationException;
import br.com.meetime.hubspot.security.WebhookSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

@Component
public class WebhookSignatureVerifierImpl implements WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifierImpl.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String clientSecret;

    public WebhookSignatureVerifierImpl(HubSpotConfig hubSpotConfig) {
        this.clientSecret = Objects.requireNonNull(
                hubSpotConfig.getWebhook().getClientSecret(),
                "HUBSPOT WEBHOOK CLIENT SECRET CANNOT BE NULL IN CONFIGURATION (HUBSPOTCONFIG)"
        );
        if (this.clientSecret.isBlank()) {
            log.error("HUBSPOT WEBHOOK CLIENT SECRET IS BLANK IN CONFIGURATION!");
            throw new IllegalArgumentException("HubSpot Webhook Client Secret cannot be blank");
        }
    }

    @Override
    public void validateSignatureV3(String receivedSignature, String requestBody) {
        if (receivedSignature == null || receivedSignature.isBlank()) {
            log.warn("WEBHOOK REQUEST RECEIVED WITHOUT V3 SIGNATURE (X-HUBSPOT-SIGNATURE-V3).");
            throw new WebhookAuthenticationException("Webhook V3 signature is missing.");
        }
        if (requestBody == null) {
            log.error("NULL REQUEST BODY RECEIVED FOR V3 SIGNATURE VALIDATION.");
            throw new WebhookAuthenticationException("Request body cannot be null for V3 signature validation.");
        }

        String computedSignature = computeSignatureV3(requestBody);

        if (!TimingSafeComparator.isEqual(computedSignature.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8))) {
            log.warn("INVALID WEBHOOK V3 SIGNATURE. EXPECTED (COMPUTED): [{}], RECEIVED: [{}]", computedSignature, receivedSignature);
            throw new WebhookAuthenticationException("Invalid webhook V3 signature.");
        }

        log.debug("WEBHOOK V3 SIGNATURE VALIDATED SUCCESSFULLY.");
    }

    private String computeSignatureV3(String requestBody) {
        try {
            String sourceString = this.clientSecret + requestBody;

            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(this.clientSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            hmac.init(secretKeySpec);
            byte[] hash = hmac.doFinal(sourceString.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            log.error("HMACSHA256 ALGORITHM NOT FOUND IN JVM.", e);
            throw new WebhookAuthenticationException("INTERNAL FAILURE CALCULATING SIGNATURE (ALGORITHM UNAVAILABLE).", e);
        } catch (InvalidKeyException e) {
            log.error("INVALID KEY FOR HMAC CALCULATION (CLIENT SECRET: '{}'). CHECK CONFIGURATION.", "****", e); // Mask secret
            throw new WebhookAuthenticationException("INTERNAL FAILURE CALCULATING SIGNATURE (INVALID KEY).", e);
        } catch (Exception e) {
            log.error("UNEXPECTED ERROR DURING V3 SIGNATURE CALCULATION.", e);
            throw new WebhookAuthenticationException("INTERNAL FAILURE CALCULATING SIGNATURE.", e);
        }
    }

    private static class TimingSafeComparator {
        public static boolean isEqual(byte[] a, byte[] b) {
            return MessageDigest.isEqual(a, b);
        }
    }
}