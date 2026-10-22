package br.com.meetime.hubspot.client;

import br.com.meetime.hubspot.domain.dto.HubSpotErrorDTO;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class HubSpotResponseErrors {
    private HubSpotResponseErrors() {}

    public static Mono<HubSpotApiException> from(ClientResponse response, String operation) {
        return response.bodyToMono(HubSpotErrorDTO.class)
                .onErrorReturn(new HubSpotErrorDTO())
                .defaultIfEmpty(new HubSpotErrorDTO())
                .map(body -> new HubSpotApiException(operation + " failed", response.statusCode(), body,
                        retryAfter(response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER))));
    }

    private static String retryAfter(String value) {
        if (value == null) {
            return null;
        }
        if (value.matches("[0-9]{1,9}")) {
            return value;
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
