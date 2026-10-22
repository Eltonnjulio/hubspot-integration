package br.com.meetime.hubspot.service;

import br.com.meetime.hubspot.domain.dto.WebhookEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ContactCreationHandler {
    private static final Logger log = LoggerFactory.getLogger(ContactCreationHandler.class);

    public void handle(WebhookEventDTO event) {
        log.info("CONTACT CREATION RECEIVED portalId={} contactId={} eventId={}",
                event.getPortalId(), event.getObjectId(), event.getEventId());
    }
}
