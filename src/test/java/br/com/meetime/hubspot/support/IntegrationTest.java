package br.com.meetime.hubspot.support;

import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
    "hubspot.oauth.client-id=test", "hubspot.oauth.client-secret=test-secret",
    "hubspot.webhook.client-secret=test-secret", "spring.security.user.name=test",
    "spring.security.user.password=test-password",
    "storage.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.datasource.url=jdbc:h2:mem:api-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureWebTestClient
public @interface IntegrationTest {}
