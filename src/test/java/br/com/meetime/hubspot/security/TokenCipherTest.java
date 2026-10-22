package br.com.meetime.hubspot.security;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class TokenCipherTest {
    private final TokenCipher cipher = new TokenCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Test
    void roundTripUsesDifferentNonces() {
        byte[] value = "sensitive-credentials".getBytes(StandardCharsets.UTF_8);
        String first = cipher.encrypt(value);
        String second = cipher.encrypt(value);
        assertNotEquals(first, second);
        assertArrayEquals(value, cipher.decrypt(first));
        assertArrayEquals(value, cipher.decrypt(second));
    }

    @Test
    void tamperingAndWrongKeyFailClosed() {
        String payload = cipher.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        byte[] tampered = Base64.getDecoder().decode(payload);
        tampered[tampered.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered)));
        var differentKey = new TokenCipher("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        assertThrows(IllegalStateException.class, () -> differentKey.decrypt(payload));
    }

    @Test
    void invalidKeysAreRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new TokenCipher("not-base64"));
        assertThrows(IllegalArgumentException.class, () -> new TokenCipher("dGVzdA=="));
    }
}
