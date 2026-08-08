package com.filmforest.content.poster;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbCredentialVerifierTest {

    @Test
    void statusCodesAreReducedToNonSensitiveValidationCategories() {
        assertThat(TmdbCredentialVerifier.classify(200).status()).isEqualTo("valid");
        assertThat(TmdbCredentialVerifier.classify(401).status()).isEqualTo("invalid");
        assertThat(TmdbCredentialVerifier.classify(429).status()).isEqualTo("rate_limited");
        assertThat(TmdbCredentialVerifier.classify(503).status()).isEqualTo("unavailable");
        assertThat(TmdbCredentialVerifier.classify(400).errorCode()).isEqualTo("request_rejected");
    }
}
