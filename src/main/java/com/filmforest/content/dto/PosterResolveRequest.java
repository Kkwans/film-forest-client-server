package com.filmforest.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PosterResolveRequest(
        @NotEmpty @Size(max = 100) List<@Valid PosterContentRef> items
) { }
