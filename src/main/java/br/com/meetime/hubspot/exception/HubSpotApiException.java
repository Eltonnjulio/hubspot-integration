package br.com.meetime.hubspot.exception;

import br.com.meetime.hubspot.domain.dto.HubSpotErrorDTO;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class HubSpotApiException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final HubSpotErrorDTO errorBody;

    public HubSpotApiException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorBody = null;
    }

    public HubSpotApiException(String message, HttpStatusCode statusCode, HubSpotErrorDTO errorBody) {
        super(message);
        this.statusCode = statusCode;
        this.errorBody = errorBody;
    }

}
