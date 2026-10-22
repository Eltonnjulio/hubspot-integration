package br.com.meetime.hubspot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(exchange -> {
                    boolean safe = java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE")
                            .contains(exchange.getRequest().getMethod().name());
                    boolean webhook = exchange.getRequest().getPath().value().equals("/webhooks/contacts");
                    return safe || webhook
                            ? org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.notMatch()
                            : org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult.match();
                }))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.POST, "/webhooks/contacts").permitAll()
                        .pathMatchers(HttpMethod.GET, "/oauth/callback", "/actuator/health").permitAll()
                        .pathMatchers("/contacts/**", "/oauth/authorize", "/actuator/**", "/csrf").authenticated()
                        .anyExchange().denyAll())
                .httpBasic(Customizer.withDefaults())
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }
}
