package br.com.meetime.hubspot.config;

import br.com.meetime.hubspot.repository.WebhookEventRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class WebhookMaintenance {
    private final WebhookEventRepository events;

    public WebhookMaintenance(WebhookEventRepository events) {
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${storage.webhook-cleanup-delay:3600000}", initialDelayString = "60000")
    public void removeExpiredEvents() {
        events.deleteExpired();
    }
}
