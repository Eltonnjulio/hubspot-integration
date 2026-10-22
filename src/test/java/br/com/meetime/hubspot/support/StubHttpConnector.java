package br.com.meetime.hubspot.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpResponse;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class StubHttpConnector implements ClientHttpConnector {
    public record Request(HttpMethod method, URI uri, HttpHeaders headers, String body) {}
    private final Function<Request, MockClientHttpResponse> handler;
    private final AtomicInteger calls = new AtomicInteger();

    public StubHttpConnector(Function<Request, MockClientHttpResponse> handler) {
        this.handler = handler;
    }

    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
            Function<? super ClientHttpRequest, Mono<Void>> callback) {
        return Mono.defer(() -> {
            calls.incrementAndGet();
            var request = new MockClientHttpRequest(method, uri);
            return callback.apply(request).then(Mono.defer(() -> request.getBodyAsString().defaultIfEmpty("")
                    .map(body -> handler.apply(new Request(method, uri, request.getHeaders(), body)))));
        });
    }

    public int calls() {
        return calls.get();
    }
}
