package com.filmforest.content.poster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.mapper.ContentPosterMatchMapper;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentPosterMatchServiceTest {

    private final ContentPosterMatchMapper mapper = mock(ContentPosterMatchMapper.class);
    private final ContentPosterMatchService service =
            new ContentPosterMatchService(mapper, new ObjectMapper());

    @Test
    void acceptedPosterUsesOnlyAllowlistedTmdbImageBase() {
        ContentPosterMatch match = accepted("/poster.jpg",
                "{\"secureImageBaseUrl\":\"https://image.tmdb.org/t/p/\",\"posterSize\":\"w342\"}");

        assertThat(service.acceptedPosterUrl(match))
                .isEqualTo("https://image.tmdb.org/t/p/w342/poster.jpg");
    }

    @Test
    void untrustedDiagnosticFallsBackToSafeTmdbAddress() {
        ContentPosterMatch match = accepted("/poster.jpg",
                "{\"secureImageBaseUrl\":\"https://evil.example/\",\"posterSize\":\"w500\"}");

        assertThat(service.acceptedPosterUrl(match))
                .isEqualTo("https://image.tmdb.org/t/p/w500/poster.jpg");
    }

    @Test
    void saveMapsTmdbContentVotesWithoutTouchingOtherSourceScores() {
        when(mapper.selectOne(any())).thenReturn(null);
        TmdbPosterMatcher.MatchResult result = new TmdbPosterMatcher.MatchResult(
                TmdbPosterMatcher.Status.ACCEPTED,
                new TmdbPosterMatcher.SearchCandidate(44, TmdbPosterMatcher.MediaType.MOVIE,
                        "测试电影", "Test Movie", 2024, "/poster.jpg", 8.6, 987),
                new TmdbPosterMatcher.PosterAsset("/poster.jpg", "zh", 4.2, 3),
                new TmdbPosterMatcher.ImageConfiguration("https://image.tmdb.org/t/p/", List.of("w500")),
                new BigDecimal("0.9900"), Map.of("reason", "matched"));

        ContentPosterMatch saved = service.save(ContentType.MOVIE, 8L, "https://source.example/poster.jpg", result);

        assertThat(saved.getTmdbScore()).isEqualByComparingTo("8.6");
        assertThat(saved.getTmdbVoteCount()).isEqualTo(987);
        assertThat(saved.getPosterPath()).isEqualTo("/poster.jpg");
        verify(mapper).insert(saved);
    }

    private ContentPosterMatch accepted(String posterPath, String diagnostic) {
        ContentPosterMatch match = new ContentPosterMatch();
        match.setMatchStatus("accepted");
        match.setPosterPath(posterPath);
        match.setDiagnostic(diagnostic);
        return match;
    }
}
