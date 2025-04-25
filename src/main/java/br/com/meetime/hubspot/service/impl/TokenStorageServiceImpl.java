package br.com.meetime.hubspot.service.impl;

import br.com.meetime.hubspot.domain.response.HubSpotTokenResponse;
import br.com.meetime.hubspot.service.TokenStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class TokenStorageServiceImpl implements TokenStorageService {

    private static final Logger log = LoggerFactory.getLogger(TokenStorageServiceImpl.class);

    private HubSpotTokenResponse currentToken;
    private Instant tokenExpiryTime;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void saveToken(HubSpotTokenResponse tokenResponse) {
        lock.writeLock().lock();
        try {
            this.currentToken = tokenResponse;
            this.tokenExpiryTime = Instant.now().plusSeconds(tokenResponse.getExpiresIn() - 60);
            log.info("ACCESS TOKEN STORED. EXPIRATION TIME SET TO: {}", tokenExpiryTime);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<String> getAccessToken() {
        lock.readLock().lock();
        try {
            if (currentToken != null && Instant.now().isBefore(tokenExpiryTime)) {
                log.debug("VALID ACCESS TOKEN FOUND IN MEMORY.");
                return Optional.of(currentToken.getAccessToken());
            }

            if (currentToken != null) {
                log.warn("EXPIRED OR INVALID ACCESS TOKEN DETECTED. CLEARING FROM MEMORY.");
                clearToken();
            } else {
                log.debug("NO ACCESS TOKEN FOUND IN MEMORY.");
            }

            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<String> getRefreshToken() {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(currentToken)
                    .map(HubSpotTokenResponse::getRefreshToken);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clearToken() {
        lock.writeLock().lock();
        try {
            currentToken = null;
            tokenExpiryTime = null;
            log.info("ACCESS TOKEN CLEARED FROM MEMORY.");
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean hasValidToken() {
        return getAccessToken().isPresent();
    }
}
