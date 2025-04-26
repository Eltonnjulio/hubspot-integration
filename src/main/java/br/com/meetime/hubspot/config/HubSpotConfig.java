package br.com.meetime.hubspot.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "hubspot")
@Getter
@Setter
@Validated
public class HubSpotConfig {

    @NotNull
    private Api api;
    @NotNull
    private OAuthConfig oauth;
    @NotNull
    private WebhookConfig webhook;

    @Getter
    @Setter
    public static class Api {
        @NotBlank
        private String baseUrl;
    }

    @Getter
    @Setter
    public static class OAuthConfig {
        @NotBlank
        private String clientId;
        @NotBlank
        private String clientSecret;
        @NotBlank
        private String redirectUri;
        @NotBlank
        private String authorizationUri;
        @NotBlank
        private String tokenUri;
        @NotBlank
        private String scopes;

        public Set<String> getScopeSet() {
            return Set.of(scopes.split("\\s+"));
        }

        public String getEncodedScopes() {
            return getScopeSet().stream().collect(Collectors.joining("%20"));
        }
    }

    @Getter
    @Setter
    public static class WebhookConfig {
        @NotBlank
        private String clientSecret;
    }


    public String getFullTokenUri() {
        if (getOauth().getTokenUri().startsWith("/")) {
            return getApi().getBaseUrl() + getOauth().getTokenUri();
        }
        return getOauth().getTokenUri();
    }

    public String getFullAuthorizationUri() {
        return getOauth().getAuthorizationUri();
    }
}