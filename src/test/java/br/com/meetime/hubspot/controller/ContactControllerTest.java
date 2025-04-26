package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.domain.request.ContactCreateRequest;
import br.com.meetime.hubspot.domain.request.ContactPropertiesRequest;
import br.com.meetime.hubspot.domain.response.ContactResponse;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    @Mock
    private ContactService contactService;

    @InjectMocks
    private ContactController contactController;

    private ContactCreateRequest validRequest;
    private ContactResponse successResponse;

    @BeforeEach
    void setUp() {
        ContactPropertiesRequest properties = new ContactPropertiesRequest();
        properties.setEmail("test@example.com");
        properties.setFirstname("Test");
        validRequest = new ContactCreateRequest();
        validRequest.setProperties(properties);

        successResponse = new ContactResponse();
        successResponse.setId("contact123");
    }

    @Test
    @DisplayName("createContact should return CREATED with body when service returns success")
    void createContact_shouldReturnCreatedWithBody_whenServiceSucceeds() {
        when(contactService.createContact(any(ContactCreateRequest.class)))
                .thenReturn(Mono.just(successResponse));

        Mono<ResponseEntity<ContactResponse>> result = contactController.createContact(validRequest);

        StepVerifier.create(result)
                .expectNextMatches(responseEntity ->
                        responseEntity.getStatusCode() == HttpStatus.CREATED &&
                                responseEntity.getBody() != null &&
                                "contact123".equals(responseEntity.getBody().getId())
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("createContact should propagate error when service returns error")
    void createContact_shouldPropagateError_whenServiceFails() {
        HubSpotApiException serviceException = new HubSpotApiException("API Error", HttpStatus.BAD_REQUEST);
        when(contactService.createContact(any(ContactCreateRequest.class)))
                .thenReturn(Mono.error(serviceException));

        Mono<ResponseEntity<ContactResponse>> result = contactController.createContact(validRequest);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof HubSpotApiException &&
                                throwable.getMessage().equals("API Error")
                )
                .verify();
    }

    @Test
    @DisplayName("createContact should return error if request validation fails (simulated)")
    void createContact_shouldFail_whenRequestValidationFails() {
        RuntimeException validationException = new RuntimeException("Validation failed upstream");
        when(contactService.createContact(any(ContactCreateRequest.class)))
                .thenReturn(Mono.error(validationException));

        Mono<ResponseEntity<ContactResponse>> result = contactController.createContact(validRequest);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable == validationException)
                .verify();
    }
}