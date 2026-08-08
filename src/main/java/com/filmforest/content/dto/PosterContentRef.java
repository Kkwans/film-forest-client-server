package com.filmforest.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PosterContentRef(
        @NotBlank String contentType,
        @Positive long contentId
) { }
