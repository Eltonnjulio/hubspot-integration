package br.com.meetime.hubspot.security;

public interface WebhookSignatureVerifier {

    void validateSignatureV3(String receivedSignature, String method, String uri, String requestBody, String timestamp) ;
}
