package br.com.meetime.hubspot.exception;

import br.com.meetime.hubspot.domain.dto.HubSpotErrorDTO;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class HubSpotApiException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final HubSpotErrorDTO errorBody;
    private final String retryAfter;

    public HubSpotApiException(String message, HttpStatusCode statusCode) {
        this(message, statusCode, null, null);
    }

    public HubSpotApiException(String message, HttpStatusCode statusCode, HubSpotErrorDTO errorBody) {
        this(message, statusCode, errorBody, null);
    }

    public HubSpotApiException(String message, HttpStatusCode statusCode, HubSpotErrorDTO errorBody, String retryAfter) {
        super(message);
        this.statusCode = statusCode;
        this.errorBody = errorBody;
        this.retryAfter = retryAfter;
    }
}
