package com.filmforest.content.poster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.mapper.ContentPosterMatchMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ContentPosterMatchServiceTest {

    private final ContentPosterMatchService service =
            new ContentPosterMatchService(mock(ContentPosterMatchMapper.class), new ObjectMapper());

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

    private ContentPosterMatch accepted(String posterPath, String diagnostic) {
        ContentPosterMatch match = new ContentPosterMatch();
        match.setMatchStatus("accepted");
        match.setPosterPath(posterPath);
        match.setDiagnostic(diagnostic);
        return match;
    }
}
