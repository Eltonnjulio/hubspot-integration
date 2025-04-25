package br.com.meetime.hubspot.exception;

import br.com.meetime.hubspot.domain.response.ErrorResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HubSpotApiException.class)
    public ResponseEntity<ErrorResponse> handleHubSpotApiException(HubSpotApiException ex, WebRequest request) {
        String message = String.format("HUBSPOT API ERROR: %s (Status: %d)", ex.getMessage(), ex.getStatusCode().value());
        if (ex.getErrorBody() != null && ex.getErrorBody().getMessage() != null) {
            message = String.format("HUBSPOT API ERROR: %s (Status: %d, HubSpot Msg: %s)",
                    ex.getMessage(), ex.getStatusCode().value(), ex.getErrorBody().getMessage());
        }

        log.error(message, ex);

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                ex.getStatusCode().value(),
                HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase(),
                message,
                request.getDescription(false).replace("uri=", ""),
                null
        );
        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }

    @ExceptionHandler(WebhookAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleWebhookAuthenticationException(WebhookAuthenticationException ex, WebRequest request) {
        log.warn("WEBHOOK AUTHENTICATION FAILURE: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""),
                null
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.toList());

        log.warn("VALIDATION ERROR: {}", errors);

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation error in the request.",
                request.getDescription(false).replace("uri=", ""),
                errors
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RequestNotPermitted ex, WebRequest request) {
        log.warn("HUBSPOT API RATE LIMIT EXCEEDED: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit for the HubSpot API exceeded. Please try again later.",
                request.getDescription(false).replace("uri=", ""),
                null
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("UNEXPECTED SERVER ERROR: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An internal server error occurred.",
                request.getDescription(false).replace("uri=", ""),
                null
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
