package br.com.meetime.hubspot.service;

import br.com.meetime.hubspot.domain.request.ContactCreateRequest;
import br.com.meetime.hubspot.domain.response.ContactResponse;
import reactor.core.publisher.Mono;

public interface ContactService {
    public Mono<ContactResponse> createContact(ContactCreateRequest contactRequest);
}
