package br.com.meetime.hubspot.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(CorrelationFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String id = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Request-ID", id);
        return chain.filter(exchange).doFinally(signal -> log.info("requestId={} method={} path={} status={} outcome={}",
                id, exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                exchange.getResponse().getStatusCode(), signal));
    }
}
