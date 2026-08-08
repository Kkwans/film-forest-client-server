package com.filmforest.content.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityPropertiesTest {

    @Test
    void corsOriginsAreTrimmedAndDeduplicated() {
        CorsProperties properties = new CorsProperties(List.of(
                " http://localhost:3000 ",
                "http://localhost:3000",
                "https://film.example.test"
        ));

        assertThat(properties.allowedOrigins())
                .containsExactly("http://localhost:3000", "https://film.example.test");
    }

    @Test
    void wildcardCorsOriginIsRejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shortJwtSecretIsRejected() {
        assertThatThrownBy(() -> new JwtProperties("too-short", 60_000, "film-forest"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void posterCredentialKeyMustDecodeToExactly256Bits() {
        String valid = Base64.getEncoder().encodeToString(new byte[32]);
        assertThat(new PosterCredentialProperties(valid).encryptionKey().getEncoded()).hasSize(32);
        assertThatThrownBy(() -> new PosterCredentialProperties("not-base64").encryptionKey())
                .isInstanceOf(IllegalStateException.class);
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new PosterCredentialProperties(shortKey).encryptionKey())
                .isInstanceOf(IllegalStateException.class);
    }
}
