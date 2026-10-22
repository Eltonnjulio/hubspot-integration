package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.domain.dto.WebhookEventDTO;
import br.com.meetime.hubspot.exception.InvalidWebhookPayloadException;
import br.com.meetime.hubspot.repository.WebhookEventRepository;
import br.com.meetime.hubspot.service.ContactCreationHandler;
import br.com.meetime.hubspot.service.WebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

@Service
public class WebhookServiceImpl implements WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);
    private final ObjectMapper mapper;
    private final ContactCreationHandler handler;
    private final WebhookEventRepository repository;

    public WebhookServiceImpl(ObjectMapper mapper, ContactCreationHandler handler, WebhookEventRepository repository) {
        this.mapper = mapper;
        this.handler = handler;
        this.repository = repository;
    }

    @Override
    public void processWebhookEvents(String body) {
        List<WebhookEventDTO> events = parse(body);
        List<WebhookEventDTO> supported = events.stream()
                .filter(this::isContactCreation).toList();
        for (WebhookEventDTO event : supported) {
            if (event.getEventId() == null || event.getPortalId() == null || event.getObjectId() == null) {
                throw new InvalidWebhookPayloadException("Missing webhook event identifiers");
            }
        }
        log.info("Received webhook batch size={} supported={}", events.size(), supported.size());
        for (WebhookEventDTO event : supported) {
            repository.processOnce(event, () -> handler.handle(event));
        }
    }

    private boolean isContactCreation(WebhookEventDTO event) {
        return "contact.creation".equalsIgnoreCase(event.getSubscriptionType())
                || ("object.creation".equalsIgnoreCase(event.getSubscriptionType())
                && "0-1".equals(event.getObjectTypeId()));
    }

    private List<WebhookEventDTO> parse(String body) {
        try {
            List<WebhookEventDTO> events = mapper.readValue(body, new TypeReference<>() {});
            if (events == null || events.stream().anyMatch(Objects::isNull)) {
                throw new InvalidWebhookPayloadException("Invalid webhook events");
            }
            return events;
        } catch (JsonProcessingException e) {
            throw new InvalidWebhookPayloadException("Invalid webhook JSON", e);
        }
    }
}
