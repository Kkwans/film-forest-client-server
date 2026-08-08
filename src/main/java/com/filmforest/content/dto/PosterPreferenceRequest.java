package com.filmforest.content.dto;

import jakarta.validation.constraints.NotBlank;

public record PosterPreferenceRequest(@NotBlank String posterSource) {
}
