package com.filmforest.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@ConfigurationProperties(prefix = "app.poster")
public record PosterCredentialProperties(String credentialEncryptionKey) {

    public SecretKey encryptionKey() {
        if (credentialEncryptionKey == null || credentialEncryptionKey.isBlank()) {
            throw new IllegalStateException("海报凭据加密密钥未配置");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(credentialEncryptionKey.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("海报凭据加密密钥格式无效");
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("海报凭据加密密钥必须为 32 字节");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
