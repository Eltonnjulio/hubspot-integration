package br.com.meetime.hubspot.domain;

import java.time.Instant;

public record TokenSnapshot(String accessToken, String refreshToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "TokenSnapshot[credentials=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
