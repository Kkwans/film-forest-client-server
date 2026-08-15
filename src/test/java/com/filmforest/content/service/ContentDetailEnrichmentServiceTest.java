package com.filmforest.content.service;

import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.ContentPosterMatchService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentDetailEnrichmentServiceTest {

    private final ContentPosterMatchService matchService = mock(ContentPosterMatchService.class);
    private final ContentDetailEnrichmentService service = new ContentDetailEnrichmentService(matchService);

    @Test
    void nonAcceptedMatchIsDiagnosticOnlyAndCannotExposeTmdbVotes() {
        ContentPosterMatch pending = match("pending", new BigDecimal("8.6"), 987);
        when(matchService.find(ContentType.MOVIE, 7L)).thenReturn(pending);
        Movie movie = new Movie();
        movie.setId(7L);

        service.enrich(ContentType.MOVIE, movie);

        assertThat(movie.getTmdbScore()).isNull();
        assertThat(movie.getTmdbVoteCount()).isNull();
    }

    @Test
    void acceptedTmdbScoreAndVoteCountRemainIndependentNullableFields() {
        ContentPosterMatch accepted = match("accepted", null, 987);
        when(matchService.find(ContentType.MOVIE, 7L)).thenReturn(accepted);
        Movie movie = new Movie();
        movie.setId(7L);

        service.enrich(ContentType.MOVIE, movie);

        assertThat(movie.getTmdbScore()).isNull();
        assertThat(movie.getTmdbVoteCount()).isEqualTo(987);
    }

    private ContentPosterMatch match(String status, BigDecimal score, Integer voteCount) {
        ContentPosterMatch match = new ContentPosterMatch();
        match.setMatchStatus(status);
        match.setTmdbScore(score);
        match.setTmdbVoteCount(voteCount);
        return match;
    }
}
