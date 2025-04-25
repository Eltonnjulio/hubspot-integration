package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.domain.dto.WebhookEventDTO;
import br.com.meetime.hubspot.service.WebhookService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);
    private final ObjectMapper objectMapper;

    public WebhookServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void processWebhookEvents(String requestBody) {
        try {
            List<WebhookEventDTO> events = objectMapper.readValue(requestBody, new TypeReference<List<WebhookEventDTO>>() {});
            log.info("RECEIVED {} WEBHOOK EVENT(S).", events.size());

            events.stream()
                    .filter(event -> "contact.creation".equalsIgnoreCase(event.getSubscriptionType()))
                    .forEach(this::processContactCreationEvent);

        } catch (Exception e) {
            log.error("FAILED TO PARSE OR PROCESS WEBHOOK EVENTS: {}", e.getMessage(), e);
        }
    }

    private void processContactCreationEvent(WebhookEventDTO event) {
        if (event.getObjectId() == null) {
            log.warn("RECEIVED CONTACT.CREATION EVENT WITHOUT OBJECT ID: {}", event);
            return;
        }

        log.info("PROCESSING CONTACT.CREATION EVENT. HUBSPOT CONTACT ID: {}", event.getObjectId());

        // Future business logic placeholder
        // e.g., sync with CRM, enrich internal database, trigger messaging system, etc.

        log.info("CONTACT.CREATION EVENT FOR ID {} PROCESSED (LOG ONLY).", event.getObjectId());
    }
}
