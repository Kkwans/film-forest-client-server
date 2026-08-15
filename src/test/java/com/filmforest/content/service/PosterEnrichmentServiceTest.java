package com.filmforest.content.service;

import com.filmforest.content.dto.PosterSettingView;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.ContentPosterMatchService;
import com.filmforest.content.poster.PosterContentLoader;
import com.filmforest.content.poster.PosterContentLoader.ContentSnapshot;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher;
import com.filmforest.content.poster.tmdb.TmdbApiClient.TmdbApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PosterEnrichmentServiceTest {

    private final UserPosterSettingService settingService = mock(UserPosterSettingService.class);
    private final PosterContentLoader contentLoader = mock(PosterContentLoader.class);
    private final ContentPosterMatchService matchService = mock(ContentPosterMatchService.class);
    private final TmdbPosterMatcher matcher = mock(TmdbPosterMatcher.class);
    private final PosterEnrichmentService service =
            new PosterEnrichmentService(settingService, contentLoader, matchService, matcher);

    @Test
    void originalPreferenceNeverDecryptsCredentialOrCallsTmdb() {
        when(contentLoader.load(ContentType.MOVIE, 8L)).thenReturn(content());
        when(settingService.get(7L)).thenReturn(setting("original", false));

        var result = service.enrich(7L, "movie", 8L);

        assertThat(result.source()).isEqualTo("original");
        assertThat(result.diagnosticCode()).isEqualTo("preference_original");
        verify(settingService, never()).requireCredential(7L);
        verifyNoInteractions(matcher);
    }

    @Test
    void acceptedSharedMatchIsReusedWithoutAnotherExternalRequest() {
        ContentPosterMatch accepted = new ContentPosterMatch();
        accepted.setContentType("movie");
        accepted.setContentId(8L);
        accepted.setMatchStatus("accepted");
        when(contentLoader.load(ContentType.MOVIE, 8L)).thenReturn(content());
        when(settingService.get(7L)).thenReturn(setting("tmdb", true));
        when(matchService.find(ContentType.MOVIE, 8L)).thenReturn(accepted);
        when(matchService.acceptedPosterUrl(accepted))
                .thenReturn("https://image.tmdb.org/t/p/w500/poster.jpg");

        var result = service.enrich(7L, "movie", 8L);

        assertThat(result.source()).isEqualTo("tmdb");
        assertThat(result.posterUrl()).endsWith("/poster.jpg");
        accepted.setTmdbScore(new java.math.BigDecimal("8.4"));
        accepted.setTmdbVoteCount(321);
        var scoreResult = service.enrich(7L, "movie", 8L);
        assertThat(scoreResult.tmdbScore()).isEqualByComparingTo("8.4");
        assertThat(scoreResult.tmdbVoteCount()).isEqualTo(321);
        verify(settingService, never()).requireCredential(7L);
        verifyNoInteractions(matcher);
    }

    @Test
    void externalFailureReturnsOriginalPosterAndPersistsNoSourceScore() {
        when(contentLoader.load(ContentType.MOVIE, 8L)).thenReturn(content());
        when(settingService.get(7L)).thenReturn(setting("tmdb", true));
        when(matchService.find(ContentType.MOVIE, 8L)).thenReturn(null);
        when(settingService.requireCredential(7L))
                .thenReturn(new UserPosterSettingService.DecryptedCredential("api_key", "test-credential"));
        when(matcher.match(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new TmdbApiException("network_error", 0));
        ContentPosterMatch error = new ContentPosterMatch();
        error.setMatchStatus("error");
        when(matchService.saveError(ContentType.MOVIE, 8L, "https://source.example/poster.jpg",
                "network_error", 0)).thenReturn(error);

        var result = service.enrich(7L, "movie", 8L);

        assertThat(result.source()).isEqualTo("original");
        assertThat(result.posterUrl()).isNull();
        assertThat(result.diagnosticCode()).isEqualTo("network_error");
        assertThat(result.tmdbScore()).isNull();
        assertThat(result.tmdbVoteCount()).isNull();
        verify(matchService).saveError(ContentType.MOVIE, 8L, "https://source.example/poster.jpg",
                "network_error", 0);
    }

    @Test
    void pendingMatchKeepsDiagnosticStateButDoesNotExposeTmdbVotesOnFallback() {
        ContentPosterMatch pending = new ContentPosterMatch();
        pending.setMatchStatus("pending");
        pending.setTmdbScore(new BigDecimal("8.6"));
        pending.setTmdbVoteCount(987);
        pending.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        when(settingService.get(7L)).thenReturn(setting("tmdb", true));
        when(matchService.find(ContentType.MOVIE, 8L)).thenReturn(pending);
        when(matchService.acceptedPosterUrl(pending)).thenReturn(null);

        var result = service.enrich(7L, "movie", 8L);

        assertThat(result.source()).isEqualTo("original");
        assertThat(result.matchStatus()).isEqualTo("pending");
        assertThat(result.tmdbScore()).isNull();
        assertThat(result.tmdbVoteCount()).isNull();
        verify(settingService, never()).requireCredential(7L);
        verifyNoInteractions(matcher);
    }

    private PosterSettingView setting(String source, boolean configured) {
        return new PosterSettingView(source, configured, configured ? "api_key" : null,
                configured ? "••••1234" : null, configured ? "valid" : "not_configured", null, null);
    }

    private ContentSnapshot content() {
        return new ContentSnapshot(ContentType.MOVIE, 8L, "测试电影", List.of("Test Movie"),
                2024, "https://source.example/poster.jpg");
    }
}
