package br.com.meetime.hubspot.security;

public interface WebhookSignatureVerifier {
    void validateSignatureV3(String signature, String method, String uri, String body, String timestamp);
}
