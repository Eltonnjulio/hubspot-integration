package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.domain.dto.HubSpotErrorDTO;
import br.com.meetime.hubspot.domain.request.ContactCreateRequest;
import br.com.meetime.hubspot.domain.request.ContactPropertiesRequest;
import br.com.meetime.hubspot.domain.response.ContactResponse;
import br.com.meetime.hubspot.exception.HubSpotApiException;
import br.com.meetime.hubspot.service.HubSpotOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Function;
import java.util.function.Predicate;
import java.nio.charset.StandardCharsets;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class ContactServiceImplTest {

    @Mock
    private WebClient hubSpotWebClient;
    @Mock
    private HubSpotOAuthService hubSpotOAuthService;

    @InjectMocks
    private ContactServiceImpl contactService;

    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    @Captor ArgumentCaptor<ContactCreateRequest> contactRequestCaptor;

    private ContactCreateRequest validContactRequest;
    private final String validAccessToken = "valid-access-token";

    @BeforeEach
    void setUp() {
        ContactPropertiesRequest properties = new ContactPropertiesRequest();
        properties.setEmail("test@example.com");
        properties.setFirstname("Test");
        properties.setLastname("User");

        validContactRequest = new ContactCreateRequest();
        validContactRequest.setProperties(properties);

        when(hubSpotWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/crm/v3/objects/contacts"))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq(HttpHeaders.AUTHORIZATION), eq("Bearer " + validAccessToken))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(ContactCreateRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("createContact should succeed")
    void createContact_shouldSucceed() {
        ContactResponse mockResponse = new ContactResponse();
        mockResponse.setId("12345");

        when(hubSpotOAuthService.getValidAccessToken()).thenReturn(Mono.just(validAccessToken));
        when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ContactResponse.class)).thenReturn(Mono.just(mockResponse));

        Mono<ContactResponse> result = contactService.createContact(validContactRequest);

        StepVerifier.create(result)
                .expectNextMatches(response -> "12345".equals(response.getId()))
                .verifyComplete();

        verify(requestBodySpec).bodyValue(contactRequestCaptor.capture());
        assertNotNull(contactRequestCaptor.getValue().getProperties());
        assertEquals("test@example.com", contactRequestCaptor.getValue().getProperties().getEmail());
        verify(hubSpotOAuthService).getValidAccessToken();
    }

    @Test
    @DisplayName("createContact should return error when OAuth fails")
    void createContact_shouldReturnError_whenOAuthFails() {
        when(hubSpotOAuthService.getValidAccessToken())
                .thenReturn(Mono.error(new HubSpotApiException("OAuth failed", HttpStatusCode.valueOf(401))));

        Mono<ContactResponse> result = contactService.createContact(validContactRequest);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof HubSpotApiException &&
                                ((HubSpotApiException) throwable).getStatusCode().value() == 401 &&
                                "OAuth failed".equals(throwable.getMessage())
                )
                .verify();

        verify(hubSpotWebClient, never()).post();
    }

    @Test
    @DisplayName("createContact should return HubSpotApiException when API returns error with body")
    void createContact_shouldReturnApiException_whenApiReturnsErrorWithBody() {
        HubSpotErrorDTO errorDTO = new HubSpotErrorDTO();
        errorDTO.setMessage("Contact already exists");
        errorDTO.setCategory("CONFLICT");

        HubSpotApiException expectedException = new HubSpotApiException(
                "HubSpot API request failed with status 409.",
                HttpStatus.CONFLICT,
                errorDTO
        );

        when(hubSpotOAuthService.getValidAccessToken()).thenReturn(Mono.just(validAccessToken));
        when(responseSpec.bodyToMono(ContactResponse.class)).thenReturn(Mono.error(expectedException));
        when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);

        Mono<ContactResponse> result = contactService.createContact(validContactRequest);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof HubSpotApiException &&
                                throwable == expectedException
                )
                .verify();

        verify(hubSpotOAuthService).getValidAccessToken();
        verify(requestHeadersSpec).retrieve();
    }

    @Test
    @DisplayName("createContact should return HubSpotApiException when API returns error without body")
    void createContact_shouldReturnApiException_whenApiReturnsErrorWithoutBody() {
        HubSpotApiException expectedException = new HubSpotApiException(
                "HubSpot API request failed with status 500 and no error body.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        when(hubSpotOAuthService.getValidAccessToken()).thenReturn(Mono.just(validAccessToken));
        when(responseSpec.bodyToMono(ContactResponse.class)).thenReturn(Mono.error(expectedException));
        when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);

        Mono<ContactResponse> result = contactService.createContact(validContactRequest);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof HubSpotApiException &&
                                throwable == expectedException
                )
                .verify();

        verify(hubSpotOAuthService).getValidAccessToken();
        verify(requestHeadersSpec).retrieve();
    }

    @Test
    @DisplayName("createContact should return generic error when WebClient throws unexpected")
    void createContact_shouldReturnGenericError_whenWebClientThrowsUnexpected() {
        RuntimeException webClientException = new RuntimeException("Network Error");
        when(hubSpotOAuthService.getValidAccessToken()).thenReturn(Mono.just(validAccessToken));
        when(responseSpec.bodyToMono(ContactResponse.class)).thenReturn(Mono.error(webClientException));
        when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);


        Mono<ContactResponse> result = contactService.createContact(validContactRequest);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                "Network Error".equals(throwable.getMessage())
                )
                .verify();

        verify(hubSpotOAuthService).getValidAccessToken();
        verify(requestHeadersSpec).retrieve();
    }
}