package br.com.meetime.hubspot.repository;

import br.com.meetime.hubspot.domain.TokenSnapshot;
import java.util.Optional;

public interface TokenRepository {
    Optional<TokenSnapshot> find();
    void save(TokenSnapshot token);
    void delete();
}
