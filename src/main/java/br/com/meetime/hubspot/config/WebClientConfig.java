package br.com.meetime.hubspot.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
public class WebClientConfig {
    @Bean
    public RateLimiter hubspotRateLimiter(
            @Value("${resilience4j.ratelimiter.instances.hubspotApi.limitForPeriod:9}") int limit,
            @Value("${resilience4j.ratelimiter.instances.hubspotApi.limitRefreshPeriod:1s}") Duration period) {
        return RateLimiter.of("hubspotApi", RateLimiterConfig.custom()
                .limitForPeriod(limit).limitRefreshPeriod(period).timeoutDuration(Duration.ZERO).build());
    }

    @Bean
    public WebClient hubSpotWebClient(WebClient.Builder builder, HubSpotConfig config, HttpClientProperties properties) {
        return configured(builder, properties)
                .baseUrl(config.getApi().getBaseUrl())
                .build();
    }

    @Bean
    public WebClient hubSpotOAuthWebClient(WebClient.Builder builder, HubSpotConfig config, HttpClientProperties properties) {
        return configured(builder, properties)
                .baseUrl(config.getFullTokenUri())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
    }

    private WebClient.Builder configured(WebClient.Builder builder, HttpClientProperties properties) {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
                .responseTimeout(properties.readTimeout());
        return builder.clone().clientConnector(new ReactorClientHttpConnector(client))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }
}
