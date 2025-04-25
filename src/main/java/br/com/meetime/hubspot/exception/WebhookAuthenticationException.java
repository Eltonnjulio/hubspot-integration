package br.com.meetime.hubspot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class WebhookAuthenticationException extends RuntimeException {
    public WebhookAuthenticationException(String message) {
        super(message);
    }
     public WebhookAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}