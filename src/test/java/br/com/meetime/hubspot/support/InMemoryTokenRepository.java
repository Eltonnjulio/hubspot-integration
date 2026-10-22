package br.com.meetime.hubspot.support;

import br.com.meetime.hubspot.domain.TokenSnapshot;
import br.com.meetime.hubspot.repository.TokenRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryTokenRepository implements TokenRepository {
    private final AtomicReference<TokenSnapshot> state = new AtomicReference<>();

    @Override
    public Optional<TokenSnapshot> find() {
        return Optional.ofNullable(state.get());
    }

    @Override
    public void save(TokenSnapshot token) {
        state.set(token);
    }

    @Override
    public void delete() {
        state.set(null);
    }
}
