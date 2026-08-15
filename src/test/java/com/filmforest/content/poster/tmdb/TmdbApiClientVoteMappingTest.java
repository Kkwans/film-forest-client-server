package com.filmforest.content.poster.tmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.filmforest.content.poster.tmdb.TmdbPosterMatcher.Credential;
import static com.filmforest.content.poster.tmdb.TmdbPosterMatcher.CredentialType;
import static com.filmforest.content.poster.tmdb.TmdbPosterMatcher.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TmdbApiClientVoteMappingTest {

    @Test
    void parsesContentVotesFromOfficialMovieSearchResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"results":[{"id":11,"title":"测试电影","original_title":"Test Movie",
                "release_date":"2024-01-01","poster_path":"/poster.jpg",
                "vote_average":8.6,"vote_count":1234}]}
                """);
        when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);

        var candidates = new TmdbApiClient(httpClient, new ObjectMapper())
                .search(MediaType.MOVIE, "测试电影", 2024,
                        new Credential(CredentialType.API_KEY, "test-credential"));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.voteAverage()).isEqualTo(8.6);
            assertThat(candidate.voteCount()).isEqualTo(1234);
        });
    }
}
