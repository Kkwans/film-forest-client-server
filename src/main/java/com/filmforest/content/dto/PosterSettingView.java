package com.filmforest.content.dto;

import java.time.LocalDateTime;

public record PosterSettingView(
        String posterSource,
        boolean configured,
        String credentialType,
        String maskedHint,
        String validationStatus,
        String validationErrorCode,
        LocalDateTime validatedAt
) {
}
