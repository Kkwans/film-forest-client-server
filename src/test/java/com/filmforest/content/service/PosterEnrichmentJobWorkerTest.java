package com.filmforest.content.service;

import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.entity.PosterEnrichmentJob;
import com.filmforest.content.mapper.PosterEnrichmentJobMapper;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.ContentPosterMatchService;
import com.filmforest.content.poster.PosterBatchContentSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PosterEnrichmentJobWorkerTest {

    private final PosterEnrichmentJobMapper mapper = mock(PosterEnrichmentJobMapper.class);
    private final PosterBatchContentSource source = mock(PosterBatchContentSource.class);
    private final ContentPosterMatchService matchService = mock(ContentPosterMatchService.class);
    private final PosterEnrichmentService enrichmentService = mock(PosterEnrichmentService.class);
    private final PosterEnrichmentJobWorker worker =
            new PosterEnrichmentJobWorker(mapper, source, matchService, enrichmentService);

    @Test
    void alreadyAcceptedRowsAdvanceProgressWithoutExternalEnrichment() {
        PosterEnrichmentJob job = job();
        ContentPosterMatch accepted = new ContentPosterMatch();
        when(mapper.selectById(41L)).thenReturn(job);
        when(mapper.update(any(), any())).thenReturn(1);
        when(source.nextIds(ContentType.MOVIE, 0L, 50)).thenReturn(List.of(8L));
        when(source.nextIds(ContentType.MOVIE, 8L, 50)).thenReturn(List.of());
        when(matchService.find(ContentType.MOVIE, 8L)).thenReturn(accepted);
        when(matchService.acceptedPosterUrl(accepted)).thenReturn("https://image.tmdb.org/t/p/w500/a.jpg");

        worker.run(41L);

        verify(enrichmentService, never()).enrich(7L, "movie", 8L);
    }

    @Test
    void queuedCancellationFinishesWithoutReadingContent() {
        PosterEnrichmentJob job = job();
        job.setCancelRequested(1);
        job.setStatus("cancel_requested");
        when(mapper.selectById(41L)).thenReturn(job);

        worker.run(41L);

        assertThat(job.getStatus()).isEqualTo("cancelled");
        verify(source, never()).nextIds(ContentType.MOVIE, 0L, 50);
    }

    private PosterEnrichmentJob job() {
        PosterEnrichmentJob job = new PosterEnrichmentJob();
        job.setId(41L);
        job.setUserId(7L);
        job.setStatus("queued");
        job.setCancelRequested(0);
        job.setContentType("movie");
        job.setTotalCount(1);
        job.setProcessedCount(0);
        job.setMatchedCount(0);
        job.setPendingCount(0);
        job.setFailedCount(0);
        return job;
    }
}
