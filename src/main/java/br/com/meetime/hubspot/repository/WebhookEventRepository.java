package br.com.meetime.hubspot.repository;

import br.com.meetime.hubspot.domain.dto.WebhookEventDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

@Repository
public class WebhookEventRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final Clock clock;

    public WebhookEventRepository(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.mapper = mapper;
        this.clock = clock;
    }

    public boolean processOnce(WebhookEventDTO event, Runnable action) {
        String key = eventKey(event);
        try {
            transaction.executeWithoutResult(status -> {
                try {
                    jdbc.update("INSERT INTO processed_webhook (event_key, expires_at) VALUES (?, ?)",
                            key, Instant.now(clock).plusSeconds(604800).toEpochMilli());
                } catch (DuplicateKeyException e) {
                    throw new AlreadyProcessed();
                }
                action.run();
            });
            return true;
        } catch (AlreadyProcessed e) {
            return false;
        }
    }

    public int deleteExpired() {
        return jdbc.update("DELETE FROM processed_webhook WHERE expires_at < ?", Instant.now(clock).toEpochMilli());
    }

    private String eventKey(WebhookEventDTO event) {
        try {
            byte[] identity = mapper.writeValueAsBytes(Arrays.asList(event.getPortalId(), event.getAppId(),
                    event.getSubscriptionId(), event.getEventId(), event.getSubscriptionType(),
                    event.getObjectId(), event.getOccurredAt()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to identify webhook event", e);
        }
    }

    private static class AlreadyProcessed extends RuntimeException {}
}
