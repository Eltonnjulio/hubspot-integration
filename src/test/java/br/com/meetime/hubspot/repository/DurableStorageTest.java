package br.com.meetime.hubspot.repository;

import br.com.meetime.hubspot.domain.dto.WebhookEventDTO;
import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.security.TokenCipher;
import br.com.meetime.hubspot.service.impl.TokenStorageServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DurableStorageTest {
    @TempDir
    Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TokenCipher cipher = new TokenCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private JdbcTemplate database() {
        String postgresUrl = System.getenv("TEST_DATABASE_URL");
        DriverManagerDataSource source;
        if (postgresUrl == null || postgresUrl.isBlank()) {
            source = new DriverManagerDataSource("jdbc:h2:file:" + directory.resolve("hubspot") + ";MODE=PostgreSQL", "sa", "");
            Flyway.configure().dataSource(source).load().migrate();
        } else {
            String schema = "test_" + directory.getFileName().toString().replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.ROOT);
            String separator = postgresUrl.contains("?") ? "&" : "?";
            source = new DriverManagerDataSource(postgresUrl + separator + "currentSchema=" + schema,
                    System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "postgres"),
                    System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "postgres"));
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
        }
        return new JdbcTemplate(source);
    }

    private WebhookEventRepository events(JdbcTemplate jdbc) {
        return new WebhookEventRepository(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())), mapper, clock);
    }

    private WebhookEventDTO event() {
        var event = new WebhookEventDTO();
        event.setPortalId(1L);
        event.setEventId(2L);
        event.setObjectId(3L);
        event.setOccurredAt(100L);
        event.setSubscriptionType("contact.creation");
        return event;
    }

    @Test
    void credentialsSurviveReopeningDatabaseAndAreEncrypted() {
        var jdbc = database();
        var storage = new TokenStorageServiceImpl(new JdbcTokenRepository(jdbc, mapper, cipher), clock);
        var token = new HubSpotTokenResponse();
        token.setAccessToken("private-access-token");
        token.setRefreshToken("private-refresh-token");
        token.setExpiresIn(3600);
        storage.saveToken(token);
        String payload = jdbc.queryForObject("SELECT payload FROM oauth_token WHERE id = 1", String.class);
        assertNotNull(payload);
        assertFalse(payload.contains(token.getAccessToken()));
        assertFalse(payload.contains(token.getRefreshToken()));
        var reopened = new TokenStorageServiceImpl(new JdbcTokenRepository(database(), mapper, cipher), clock);
        assertEquals("private-access-token", reopened.getAccessToken().orElseThrow());
        assertEquals("private-refresh-token", reopened.getRefreshToken().orElseThrow());
        reopened.clearToken();
        assertTrue(storage.getRefreshToken().isEmpty());
    }

    @Test
    void successfulWebhookRemainsDeduplicatedAfterRestart() {
        var repository = events(database());
        AtomicInteger calls = new AtomicInteger();
        assertTrue(repository.processOnce(event(), calls::incrementAndGet));
        assertFalse(events(database()).processOnce(event(), calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void failedHandlerRollsBackClaimAndCanBeRetried() {
        var repository = events(database());
        assertThrows(IllegalStateException.class, () -> repository.processOnce(event(), () -> {
            throw new IllegalStateException("temporary failure");
        }));
        assertTrue(repository.processOnce(event(), () -> {}));
    }

    @Test
    void concurrentDeliveriesExecuteOnlyOneHandler() throws Exception {
        var repository = events(database());
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { start.await(); return repository.processOnce(event(), calls::incrementAndGet); });
            var second = executor.submit(() -> { start.await(); return repository.processOnce(event(), calls::incrementAndGet); });
            start.countDown();
            boolean a = first.get(10, TimeUnit.SECONDS);
            boolean b = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(a, b);
            assertEquals(1, calls.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameEventIdForDifferentObjectsIsNotDiscarded() {
        var repository = events(database());
        var other = event();
        other.setObjectId(99L);
        assertTrue(repository.processOnce(event(), () -> {}));
        assertTrue(repository.processOnce(other, () -> {}));
    }
    @Test
    void handlerConstraintViolationIsNotMistakenForDuplicateDelivery() {
        var repository = events(database());
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> repository.processOnce(event(), () -> {
                    throw new org.springframework.dao.DuplicateKeyException("business constraint");
                }));
        assertTrue(repository.processOnce(event(), () -> {}));
    }

    @Test
    void retentionCleanupAllowsDeliveryAfterWindow() {
        var jdbc = database();
        assertTrue(events(jdbc).processOnce(event(), () -> {}));
        var later = new WebhookEventRepository(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())), mapper,
                Clock.offset(clock, java.time.Duration.ofDays(8)));
        assertEquals(1, later.deleteExpired());
        assertTrue(later.processOnce(event(), () -> {}));
    }
}
