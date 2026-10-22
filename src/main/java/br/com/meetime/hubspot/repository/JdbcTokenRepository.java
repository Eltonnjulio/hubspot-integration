package br.com.meetime.hubspot.repository;

import br.com.meetime.hubspot.domain.TokenSnapshot;
import br.com.meetime.hubspot.security.TokenCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.io.IOException;
import java.util.Optional;

@Repository
public class JdbcTokenRepository implements TokenRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TokenCipher cipher;

    public JdbcTokenRepository(JdbcTemplate jdbc, ObjectMapper mapper, TokenCipher cipher) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.cipher = cipher;
    }

    @Override
    public Optional<TokenSnapshot> find() {
        String payload = jdbc.queryForObject("SELECT payload FROM oauth_token WHERE id = 1", String.class);
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(cipher.decrypt(payload), TokenSnapshot.class));
        } catch (IOException e) {
            throw new IllegalStateException("Invalid stored token format", e);
        }
    }

    @Override
    public void save(TokenSnapshot token) {
        try {
            int count = jdbc.update("UPDATE oauth_token SET payload = ? WHERE id = 1",
                    cipher.encrypt(mapper.writeValueAsBytes(token)));
            if (count != 1) {
                throw new IllegalStateException("OAuth token storage is not initialized");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize credentials", e);
        }
    }

    @Override
    public void delete() {
        jdbc.update("UPDATE oauth_token SET payload = NULL WHERE id = 1");
    }
}
