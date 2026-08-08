package com.filmforest.content.service;

import com.filmforest.content.dto.PosterSettingView;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.ContentPosterMatchService;
import com.filmforest.content.poster.PosterContentLoader;
import com.filmforest.content.poster.PosterContentLoader.ContentSnapshot;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher;
import org.junit.jupiter.api.Test;

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
