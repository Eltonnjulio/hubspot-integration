package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.domain.TokenSnapshot;
import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.repository.TokenRepository;
import br.com.meetime.hubspot.service.TokenStorageService;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class TokenStorageServiceImpl implements TokenStorageService {
    private final TokenRepository repository;
    private final Clock clock;

    public TokenStorageServiceImpl(TokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public synchronized void saveToken(HubSpotTokenResponse token) {
        if (token == null || token.getAccessToken() == null || token.getAccessToken().isBlank()
                || token.getExpiresIn() <= 0) {
            throw new IllegalArgumentException("Invalid OAuth token response");
        }
        String refresh = token.getRefreshToken();
        if (refresh == null || refresh.isBlank()) {
            refresh = repository.find().map(TokenSnapshot::refreshToken).orElse(null);
        }
        long margin = Math.min(60, token.getExpiresIn() / 10);
        repository.save(new TokenSnapshot(token.getAccessToken(), refresh,
                Instant.now(clock).plusSeconds(token.getExpiresIn() - margin)));
    }

    @Override
    public Optional<String> getAccessToken() {
        return repository.find().filter(token -> Instant.now(clock).isBefore(token.expiresAt()))
                .map(TokenSnapshot::accessToken);
    }

    @Override
    public Optional<String> getRefreshToken() {
        return repository.find().map(TokenSnapshot::refreshToken);
    }

    @Override
    public synchronized void clearToken() {
        repository.delete();
    }

    @Override
    public boolean hasValidToken() {
        return getAccessToken().isPresent();
    }
}
