package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.filmforest.content.dto.PosterResolutionView;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.entity.PosterEnrichmentJob;
import com.filmforest.content.mapper.PosterEnrichmentJobMapper;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.ContentPosterMatchService;
import com.filmforest.content.poster.PosterBatchContentSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Component
public class PosterEnrichmentJobWorker {

    private static final int PAGE_SIZE = 50;
    private static final long REQUEST_SPACING_MILLIS = 300L;
    private static final Set<String> ACTIVE_STATUSES = Set.of("queued", "running", "cancel_requested");

    private final PosterEnrichmentJobMapper mapper;
    private final PosterBatchContentSource contentSource;
    private final ContentPosterMatchService matchService;
    private final PosterEnrichmentService enrichmentService;

    public PosterEnrichmentJobWorker(PosterEnrichmentJobMapper mapper,
                                     PosterBatchContentSource contentSource,
                                     ContentPosterMatchService matchService,
                                     PosterEnrichmentService enrichmentService) {
        this.mapper = mapper;
        this.contentSource = contentSource;
        this.matchService = matchService;
        this.enrichmentService = enrichmentService;
    }

    @Async("posterEnrichmentExecutor")
    public void run(long jobId) {
        PosterEnrichmentJob job = mapper.selectById(jobId);
        if (job == null || !ACTIVE_STATUSES.contains(job.getStatus())) return;
        if (cancelled(job)) {
            finishCancelled(job);
            return;
        }
        if (!startRunning(jobId)) {
            PosterEnrichmentJob current = current(jobId);
            if (cancelled(current)) finishCancelled(current);
            return;
        }

        try {
            List<ContentType> types = job.getContentType() == null
                    ? List.of(ContentType.values()) : List.of(ContentType.parse(job.getContentType()));
            for (ContentType type : types) {
                long cursor = 0L;
                while (true) {
                    List<Long> ids = contentSource.nextIds(type, cursor, PAGE_SIZE);
                    if (ids.isEmpty()) break;
                    for (Long contentId : ids) {
                        job = current(jobId);
                        if (cancelled(job)) {
                            finishCancelled(job);
                            return;
                        }
                        if (!setCurrent(jobId, type, contentId)) {
                            finishCancelled(current(jobId));
                            return;
                        }

                        ContentPosterMatch existing = matchService.find(type, contentId);
                        if (matchService.acceptedPosterUrl(existing) != null) {
                            if (!advance(jobId, Outcome.MATCHED)) {
                                finishCancelled(current(jobId));
                                return;
                            }
                            continue;
                        }

                        Thread.sleep(REQUEST_SPACING_MILLIS);
                        job = current(jobId);
                        if (cancelled(job)) {
                            finishCancelled(job);
                            return;
                        }
                        PosterResolutionView result = enrichmentService.enrich(job.getUserId(), type.code(), contentId);
                        if ("tmdb".equals(result.source())) {
                            if (!advance(jobId, Outcome.MATCHED)) {
                                finishCancelled(current(jobId));
                                return;
                            }
                        } else if ("error".equals(result.matchStatus())) {
                            if (!advance(jobId, Outcome.FAILED)) {
                                finishCancelled(current(jobId));
                                return;
                            }
                            finishEarly(jobId, result.diagnosticCode() == null ? "tmdb_error" : result.diagnosticCode());
                            return;
                        } else if ("credential_not_configured".equals(result.diagnosticCode())) {
                            if (!advance(jobId, Outcome.FAILED)) {
                                finishCancelled(current(jobId));
                                return;
                            }
                            finishEarly(jobId, "credential_not_configured");
                            return;
                        } else {
                            if (!advance(jobId, Outcome.PENDING)) {
                                finishCancelled(current(jobId));
                                return;
                            }
                        }
                    }
                    cursor = ids.get(ids.size() - 1);
                }
            }
            job = current(jobId);
            String status = value(job.getFailedCount()) > 0 || value(job.getPendingCount()) > 0
                    ? "partial_success" : "success";
            finishActive(jobId, status, null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            finishSafely(jobId, "interrupted", "worker_interrupted");
        } catch (RuntimeException unexpected) {
            finishSafely(jobId, "failed", "internal_error");
        }
    }

    private boolean startRunning(long jobId) {
        LocalDateTime now = now();
        return mapper.update(null, new LambdaUpdateWrapper<PosterEnrichmentJob>()
                .eq(PosterEnrichmentJob::getId, jobId)
                .eq(PosterEnrichmentJob::getStatus, "queued")
                .eq(PosterEnrichmentJob::getCancelRequested, 0)
                .set(PosterEnrichmentJob::getStatus, "running")
                .set(PosterEnrichmentJob::getStartedAt, now)
                .set(PosterEnrichmentJob::getHeartbeatAt, now)) == 1;
    }

    private boolean setCurrent(long jobId, ContentType type, long contentId) {
        return mapper.update(null, new LambdaUpdateWrapper<PosterEnrichmentJob>()
                .eq(PosterEnrichmentJob::getId, jobId)
                .eq(PosterEnrichmentJob::getStatus, "running")
                .eq(PosterEnrichmentJob::getCancelRequested, 0)
                .set(PosterEnrichmentJob::getCurrentContentType, type.code())
                .set(PosterEnrichmentJob::getCurrentContentId, contentId)
                .set(PosterEnrichmentJob::getHeartbeatAt, now())) == 1;
    }

    private boolean advance(long jobId, Outcome outcome) {
        LambdaUpdateWrapper<PosterEnrichmentJob> update = new LambdaUpdateWrapper<PosterEnrichmentJob>()
                .eq(PosterEnrichmentJob::getId, jobId)
                .eq(PosterEnrichmentJob::getStatus, "running")
                .eq(PosterEnrichmentJob::getCancelRequested, 0)
                .setSql("processed_count = processed_count + 1")
                .set(PosterEnrichmentJob::getHeartbeatAt, now());
        switch (outcome) {
            case MATCHED -> update.setSql("matched_count = matched_count + 1");
            case PENDING -> update.setSql("pending_count = pending_count + 1");
            case FAILED -> update.setSql("failed_count = failed_count + 1");
        }
        return mapper.update(null, update) == 1;
    }

    private void finishEarly(long jobId, String errorCode) {
        PosterEnrichmentJob job = current(jobId);
        String status = value(job.getProcessedCount()) > 0 ? "partial_success" : "failed";
        finishActive(jobId, status, errorCode);
    }

    private void finishSafely(long jobId, String status, String errorCode) {
        PosterEnrichmentJob current = mapper.selectById(jobId);
        if (current == null || !ACTIVE_STATUSES.contains(current.getStatus())) return;
        if (cancelled(current)) finishCancelled(current);
        else finishActive(jobId, status, errorCode);
    }

    private void finishActive(long jobId, String status, String errorCode) {
        LocalDateTime now = now();
        int updated = mapper.update(null, new LambdaUpdateWrapper<PosterEnrichmentJob>()
                .eq(PosterEnrichmentJob::getId, jobId)
                .in(PosterEnrichmentJob::getStatus, "queued", "running")
                .eq(PosterEnrichmentJob::getCancelRequested, 0)
                .set(PosterEnrichmentJob::getStatus, status)
                .set(PosterEnrichmentJob::getCurrentContentType, null)
                .set(PosterEnrichmentJob::getCurrentContentId, null)
                .set(PosterEnrichmentJob::getErrorSummary, errorCode)
                .set(PosterEnrichmentJob::getHeartbeatAt, now)
                .set(PosterEnrichmentJob::getFinishedAt, now));
        if (updated == 0) {
            PosterEnrichmentJob current = mapper.selectById(jobId);
            if (current != null && cancelled(current)) finishCancelled(current);
        }
    }

    private void finishCancelled(PosterEnrichmentJob job) {
        job.setStatus("cancelled");
        job.setCancelRequested(0);
        job.setCurrentContentType(null);
        job.setCurrentContentId(null);
        job.setErrorSummary(null);
        job.setHeartbeatAt(now());
        job.setFinishedAt(now());
        mapper.updateById(job);
    }

    private PosterEnrichmentJob current(long jobId) {
        PosterEnrichmentJob current = mapper.selectById(jobId);
        if (current == null) throw new IllegalStateException("Poster enrichment job disappeared");
        return current;
    }

    private boolean cancelled(PosterEnrichmentJob job) {
        return job == null || Integer.valueOf(1).equals(job.getCancelRequested())
                || "cancel_requested".equals(job.getStatus());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private enum Outcome { MATCHED, PENDING, FAILED }
}
