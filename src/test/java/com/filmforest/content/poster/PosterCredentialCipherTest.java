package com.filmforest.content.poster;

import com.filmforest.content.config.PosterCredentialProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PosterCredentialCipherTest {

    private final PosterCredentialCipher cipher = new PosterCredentialCipher(
            new PosterCredentialProperties(Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

    @Test
    void roundTripUsesRandomIvAndUserBoundAdditionalData() {
        var first = cipher.encrypt(7L, "test-credential-value");
        var second = cipher.encrypt(7L, "test-credential-value");

        assertThat(first.iv()).hasSize(12).isNotEqualTo(second.iv());
        assertThat(first.ciphertext()).isNotEqualTo("test-credential-value".getBytes());
        assertThat(cipher.decrypt(7L, first.ciphertext(), first.iv(), first.keyVersion()))
                .isEqualTo("test-credential-value");
        assertThatThrownBy(() -> cipher.decrypt(8L, first.ciphertext(), first.iv(), first.keyVersion()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("test-credential-value");
    }

    @Test
    void springSelectsTheProductionConstructorWhenTestConstructorAlsoExists() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PosterCredentialProperties.class, () -> new PosterCredentialProperties(
                    Base64.getEncoder().encodeToString(
                            "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            context.register(PosterCredentialCipher.class);
            context.refresh();

            assertThat(context.getBean(PosterCredentialCipher.class)).isNotNull();
        }
    }
}
