package br.com.meetime.hubspot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.meetime.hubspot.service.ContactCreationHandler;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.junit.jupiter.api.Test;
import br.com.meetime.hubspot.exception.InvalidWebhookPayloadException;
import static org.junit.jupiter.api.Assertions.*;

class WebhookServiceImplTest {
    private final ContactCreationHandler handler = mock(ContactCreationHandler.class);
    private final br.com.meetime.hubspot.repository.WebhookEventRepository events = mock(br.com.meetime.hubspot.repository.WebhookEventRepository.class);
    private final WebhookServiceImpl service = new WebhookServiceImpl(new ObjectMapper(), handler, events);

    @Test
    void malformedPayloadDoesNotGetAcknowledgedAsSuccess() {
        assertThrows(InvalidWebhookPayloadException.class, () -> service.processWebhookEvents("broken"));
        assertThrows(InvalidWebhookPayloadException.class, () -> service.processWebhookEvents("null"));
        assertThrows(InvalidWebhookPayloadException.class, () -> service.processWebhookEvents("[null]"));
        assertThrows(InvalidWebhookPayloadException.class, () -> service.processWebhookEvents(
                "[{\"subscriptionType\":\"contact.creation\"}]"));
    }

    @Test
    void validEventIsDelegatedToTransactionalRepository() {
        when(events.processOnce(any(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        service.processWebhookEvents("[{\"eventId\":1,\"portalId\":2,\"objectId\":3,\"subscriptionType\":\"contact.creation\"}]");
        verify(handler).handle(any());
        verify(events).processOnce(any(), any());
    }
    @Test
    void objectCreationIsHandledOnlyForContactObjects() {
        service.processWebhookEvents("[{\"eventId\":1,\"portalId\":2,\"objectId\":3,\"subscriptionType\":\"object.creation\",\"objectTypeId\":\"0-1\"}]");
        verify(events).processOnce(any(), any());
        clearInvocations(events);
        service.processWebhookEvents("[{\"eventId\":2,\"portalId\":2,\"objectId\":4,\"subscriptionType\":\"object.creation\",\"objectTypeId\":\"0-2\"}]");
        verifyNoInteractions(events);
    }

    @Test
    void invalidLaterEventRejectsWholeBatchBeforeProcessing() {
        assertThrows(InvalidWebhookPayloadException.class, () -> service.processWebhookEvents(
                "[{\"eventId\":1,\"portalId\":2,\"objectId\":3,\"subscriptionType\":\"contact.creation\"},{\"subscriptionType\":\"contact.creation\"}]"));
        verifyNoInteractions(events, handler);
    }
}
