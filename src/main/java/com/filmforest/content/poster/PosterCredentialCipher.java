package com.filmforest.content.poster;

import com.filmforest.content.config.PosterCredentialProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

@Component
public class PosterCredentialCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_VERSION = 1;

    private final PosterCredentialProperties properties;
    private final SecureRandom secureRandom;

    public PosterCredentialCipher(PosterCredentialProperties properties) {
        this(properties, new SecureRandom());
    }

    PosterCredentialCipher(PosterCredentialProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public EncryptedCredential encrypt(long userId, String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, properties.encryptionKey(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, KEY_VERSION));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedCredential(ciphertext, iv, KEY_VERSION);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("外部凭据加密失败");
        }
    }

    public String decrypt(long userId, byte[] ciphertext, byte[] iv, int keyVersion) {
        if (keyVersion != KEY_VERSION || ciphertext == null || iv == null) {
            throw new IllegalStateException("外部凭据密文无效");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, properties.encryptionKey(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(userId, keyVersion));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("外部凭据解密失败");
        }
    }

    private byte[] aad(long userId, int keyVersion) {
        return ("film-forest:poster-credential:user:" + userId + ":v" + keyVersion)
                .getBytes(StandardCharsets.UTF_8);
    }

    public record EncryptedCredential(byte[] ciphertext, byte[] iv, int keyVersion) {
    }
}
