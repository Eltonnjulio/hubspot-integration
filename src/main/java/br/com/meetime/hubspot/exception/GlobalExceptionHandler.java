package br.com.meetime.hubspot.exception;

import br.com.meetime.hubspot.domain.response.ErrorResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HubSpotApiException.class)
    public ResponseEntity<ErrorResponse> handleHubSpotApiException(HubSpotApiException ex, ServerWebExchange request) {
        log.warn("HubSpot request failed status={} path={}", ex.getStatusCode().value(), request.getRequest().getPath().value());

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                ex.getStatusCode().value(),
                "Upstream request failed",
                "HubSpot request failed.",
                request.getRequest().getPath().value(),
                null
        );
        var headers = new org.springframework.http.HttpHeaders();
        if (ex.getRetryAfter() != null) {
            headers.set(org.springframework.http.HttpHeaders.RETRY_AFTER, ex.getRetryAfter());
        }
        return new ResponseEntity<>(errorResponse, headers, ex.getStatusCode());
    }

    @ExceptionHandler(WebhookAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleWebhookAuthenticationException(WebhookAuthenticationException ex, ServerWebExchange request) {
        log.warn("WEBHOOK AUTHENTICATION FAILURE: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ex.getMessage(),
                request.getRequest().getPath().value(),
                null
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(WebExchangeBindException ex, ServerWebExchange request) {
        List<String> errors = ex.getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.toList());

        log.warn("VALIDATION ERROR: {}", errors);

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation error in the request.",
                request.getRequest().getPath().value(),
                errors
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RequestNotPermitted ex, ServerWebExchange request) {
        log.warn("HUBSPOT API RATE LIMIT EXCEEDED: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit for the HubSpot API exceeded. Please try again later.",
                request.getRequest().getPath().value(),
                null
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(InvalidWebhookPayloadException.class)
    public ResponseEntity<ErrorResponse> handleWebhookPayload(InvalidWebhookPayloadException ex, ServerWebExchange request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(),
                400, "Bad Request", ex.getMessage(), request.getRequest().getPath().value(), null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException ex, ServerWebExchange request) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(Instant.now(),
                ex.getStatusCode().value(), "Request failed", ex.getReason(),
                request.getRequest().getPath().value(), null));
    }

    @ExceptionHandler(org.springframework.web.reactive.function.client.WebClientRequestException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamConnection(Exception ex, ServerWebExchange request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(Instant.now(),
                502, "Bad Gateway", "Unable to reach HubSpot.", request.getRequest().getPath().value(), null));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseUnavailable(Exception ex, ServerWebExchange request) {
        log.error("Database operation failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(Instant.now(),
                503, "Service Unavailable", "Storage is temporarily unavailable.",
                request.getRequest().getPath().value(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, ServerWebExchange request) {
        log.error("UNEXPECTED SERVER ERROR: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An internal server error occurred.",
                request.getRequest().getPath().value(),
                null
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
