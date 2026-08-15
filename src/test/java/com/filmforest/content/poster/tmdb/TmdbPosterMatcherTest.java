package com.filmforest.content.poster.tmdb;

import com.filmforest.content.model.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.filmforest.content.poster.tmdb.TmdbPosterMatcher.*;
import static org.assertj.core.api.Assertions.assertThat;

class TmdbPosterMatcherTest {

    private static final Credential CREDENTIAL = new Credential(CredentialType.API_KEY, "fixture-api-key");

    @Test
    void exactTitleYearAndTypeSelectChinesePosterBeforeHigherRatedEnglishPoster() {
        FakeGateway gateway = new FakeGateway();
        gateway.candidates = List.of(new SearchCandidate(101, MediaType.TV,
                "示例剧集 第二季", "Example Series", 2024, "/fallback.jpg"));
        gateway.posters = List.of(
                new PosterAsset("/english.jpg", "en", 9.9, 100),
                new PosterAsset("/chinese.jpg", "zh", 7.0, 5),
                new PosterAsset("/neutral.jpg", null, 10.0, 200));

        MatchResult result = new TmdbPosterMatcher(gateway).match(
                new MatchRequest(ContentType.DRAMA, "示例剧集 第二季 (2024)",
                        List.of("Example Series"), 2024), CREDENTIAL);

        assertThat(result.status()).isEqualTo(Status.ACCEPTED);
        assertThat(result.confidence()).isEqualByComparingTo("1.0000");
        assertThat(result.poster().filePath()).isEqualTo("/chinese.jpg");
        assertThat(result.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/chinese.jpg");
    }

    @Test
    void lowConfidenceCandidateRemainsPendingWithoutFetchingImages() {
        FakeGateway gateway = new FakeGateway();
        gateway.candidates = List.of(new SearchCandidate(202, MediaType.MOVIE,
                "完全不同", "Different", 2024, "/wrong.jpg"));

        MatchResult result = new TmdbPosterMatcher(gateway).match(
                new MatchRequest(ContentType.MOVIE, "目标电影", List.of(), 2024), CREDENTIAL);

        assertThat(result.status()).isEqualTo(Status.PENDING);
        assertThat(result.poster()).isNull();
        assertThat(gateway.posterRequests).isZero();
        assertThat(gateway.configurationRequests).isZero();
    }

    @Test
    void acceptedMatchMapsTmdbVotesAndKeepsCandidatePosterFallback() {
        FakeGateway gateway = new FakeGateway();
        gateway.candidates = List.of(new SearchCandidate(303, MediaType.MOVIE,
                "示例电影", "Example Movie", 2024, "/candidate.jpg", 8.7, 1234));
        gateway.posters = List.of();

        MatchResult result = new TmdbPosterMatcher(gateway).match(
                new MatchRequest(ContentType.MOVIE, "示例电影", List.of(), 2024), CREDENTIAL);

        assertThat(result.status()).isEqualTo(Status.ACCEPTED);
        assertThat(result.tmdbScore()).isEqualByComparingTo("8.7");
        assertThat(result.tmdbVoteCount()).isEqualTo(1234);
        assertThat(result.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/candidate.jpg");
    }

    @Test
    void credentialStringNeverEchoesSecret() {
        Credential credential = new Credential(CredentialType.READ_ACCESS_TOKEN, "do-not-print-this");

        assertThat(credential.toString()).contains("REDACTED").doesNotContain("do-not-print-this");
    }

    private static final class FakeGateway implements Gateway {
        private List<SearchCandidate> candidates = List.of();
        private List<PosterAsset> posters = List.of();
        private int posterRequests;
        private int configurationRequests;

        @Override
        public List<SearchCandidate> search(MediaType mediaType, String query, Integer year,
                                            Credential credential) {
            return candidates;
        }

        @Override
        public List<PosterAsset> posters(MediaType mediaType, long tmdbId, Credential credential) {
            posterRequests++;
            return posters;
        }

        @Override
        public ImageConfiguration configuration(Credential credential) {
            configurationRequests++;
            return new ImageConfiguration("https://image.tmdb.org/t/p/",
                    List.of("w342", "w500", "original"));
        }
    }
}
