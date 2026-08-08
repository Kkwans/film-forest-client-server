package com.filmforest.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TmdbCredentialRequest(
        @NotBlank String credentialType,
        @NotBlank @Size(min = 16, max = 2048) String credential
) {
}
