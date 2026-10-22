package br.com.meetime.hubspot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenCipher {
    private static final byte VERSION = 1;
    private static final byte[] CONTEXT = "hubspot-oauth-single-account-v1".getBytes(StandardCharsets.UTF_8);
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${storage.encryption-key}") String encodedKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Storage encryption key must be Base64 encoded");
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Storage encryption key must contain exactly 32 bytes");
        }
        key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(byte[] plaintext) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(plaintext);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(1 + nonce.length + encrypted.length)
                    .put(VERSION).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt stored credentials", e);
        }
    }

    public byte[] decrypt(String payload) {
        try {
            byte[] encoded = Base64.getDecoder().decode(payload);
            if (encoded.length < 29 || encoded[0] != VERSION) {
                throw new IllegalArgumentException("Unsupported encrypted token format");
            }
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            buffer.get();
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            return cipher(Cipher.DECRYPT_MODE, nonce).doFinal(ciphertext);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to authenticate stored credentials", e);
        }
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(CONTEXT);
        return cipher;
    }
}
