package br.com.meetime.hubspot.controller;

import br.com.meetime.hubspot.domain.request.ContactCreateRequest;
import br.com.meetime.hubspot.domain.response.ContactResponse;
import br.com.meetime.hubspot.service.ContactService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/contacts")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);
    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public Mono<ResponseEntity<ContactResponse>> createContact(@Valid @RequestBody ContactCreateRequest contactRequest) {
        log.info("REQUEST RECEIVED TO CREATE CONTACT: {}", contactRequest.getProperties().getEmail());
        return contactService.createContact(contactRequest)
                .map(createdContact -> ResponseEntity.status(HttpStatus.CREATED).body(createdContact));

    }
}