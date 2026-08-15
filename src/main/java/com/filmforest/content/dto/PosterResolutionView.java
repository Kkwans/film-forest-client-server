package com.filmforest.content.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PosterResolutionView(
        String contentType,
        long contentId,
        String posterUrl,
        String source,
        String matchStatus,
        String diagnosticCode,
        BigDecimal confidence,
        LocalDateTime matchedAt,
        BigDecimal tmdbScore,
        Integer tmdbVoteCount
) {
    public PosterResolutionView(String contentType, long contentId, String posterUrl,
                                String source, String matchStatus, String diagnosticCode,
                                BigDecimal confidence, LocalDateTime matchedAt) {
        this(contentType, contentId, posterUrl, source, matchStatus, diagnosticCode,
                confidence, matchedAt, null, null);
    }
}
