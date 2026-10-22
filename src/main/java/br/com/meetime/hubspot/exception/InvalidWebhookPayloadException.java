package br.com.meetime.hubspot.exception;

public class InvalidWebhookPayloadException extends RuntimeException {
    public InvalidWebhookPayloadException(String message) {
        super(message);
    }

    public InvalidWebhookPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
