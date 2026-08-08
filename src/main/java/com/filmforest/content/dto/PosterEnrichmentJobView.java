package com.filmforest.content.dto;

import java.time.LocalDateTime;

public record PosterEnrichmentJobView(
        long id,
        String status,
        boolean cancelRequested,
        String contentType,
        int totalCount,
        int processedCount,
        int matchedCount,
        int pendingCount,
        int failedCount,
        String currentContentType,
        Long currentContentId,
        String errorSummary,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime heartbeatAt,
        LocalDateTime finishedAt
) {
    public boolean active() {
        return status.equals("queued") || status.equals("running") || status.equals("cancel_requested");
    }
}
