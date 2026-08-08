package com.filmforest.content.poster.tmdb;

import com.filmforest.content.model.ContentType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class TmdbPosterMatcher {

    static final BigDecimal AUTO_ACCEPT_THRESHOLD = new BigDecimal("0.8500");

    private final Gateway gateway;

    public TmdbPosterMatcher(Gateway gateway) {
        this.gateway = gateway;
    }

    public MatchResult match(MatchRequest request, Credential credential) {
        MediaType mediaType = MediaType.forContent(request.contentType());
        List<SearchCandidate> candidates = gateway.search(mediaType, request.title(), request.year(), credential);
        if (candidates.isEmpty()) {
            return result(Status.NOT_FOUND, null, null, null, BigDecimal.ZERO,
                    Map.of("reason", "no_candidate"));
        }

        List<String> sourceTitles = new ArrayList<>();
        sourceTitles.add(request.title());
        sourceTitles.addAll(request.aliases());
        ScoredCandidate best = candidates.stream()
                .map(candidate -> score(candidate, mediaType, sourceTitles, request.year()))
                .max(Comparator.comparing(ScoredCandidate::score))
                .orElseThrow();

        Map<String, Object> diagnostics = diagnostics(best, request.year());
        if (best.score().compareTo(AUTO_ACCEPT_THRESHOLD) < 0) {
            return result(Status.PENDING, best.candidate(), null, null, best.score(), diagnostics);
        }

        List<PosterAsset> posters = gateway.posters(mediaType, best.candidate().id(), credential);
        PosterAsset poster = choosePoster(posters);
        if (poster == null && best.candidate().posterPath() != null) {
            poster = new PosterAsset(best.candidate().posterPath(), null, 0D, 0);
        }
        if (poster == null) {
            Map<String, Object> withoutPoster = new LinkedHashMap<>(diagnostics);
            withoutPoster.put("reason", "matched_without_poster");
            return result(Status.NOT_FOUND, best.candidate(), null, null, best.score(), Map.copyOf(withoutPoster));
        }

        ImageConfiguration configuration = gateway.configuration(credential);
        Map<String, Object> accepted = new LinkedHashMap<>(diagnostics);
        accepted.put("posterLanguage", poster.language() == null ? "null" : poster.language());
        accepted.put("posterSize", configuration.preferredPosterSize());
        accepted.put("secureImageBaseUrl", configuration.secureBaseUrl());
        return result(Status.ACCEPTED, best.candidate(), poster, configuration, best.score(), Map.copyOf(accepted));
    }

    private static ScoredCandidate score(SearchCandidate candidate, MediaType expectedType,
                                         List<String> sourceTitles, Integer sourceYear) {
        double titleScore = titleScore(candidate, sourceTitles);
        double yearScore = yearScore(sourceYear, candidate.year());
        double typeScore = candidate.mediaType() == expectedType ? 0.15D : 0D;
        BigDecimal score = BigDecimal.valueOf(Math.min(1D, titleScore + yearScore + typeScore))
                .setScale(4, RoundingMode.HALF_UP);
        return new ScoredCandidate(candidate, score, titleScore, yearScore, typeScore);
    }

    private static double titleScore(SearchCandidate candidate, List<String> sourceTitles) {
        List<String> normalizedSource = sourceTitles.stream().map(TmdbPosterMatcher::normalizeTitle)
                .filter(value -> !value.isBlank()).toList();
        for (String candidateTitle : Stream.of(candidate.title(), candidate.originalTitle()).toList()) {
            String normalizedCandidate = normalizeTitle(candidateTitle);
            if (!normalizedCandidate.isBlank() && normalizedSource.contains(normalizedCandidate)) return 0.60D;
        }
        for (String candidateTitle : Stream.of(candidate.title(), candidate.originalTitle()).toList()) {
            String normalizedCandidate = normalizeTitle(candidateTitle);
            if (!normalizedCandidate.isBlank() && normalizedSource.stream().anyMatch(value ->
                    value.contains(normalizedCandidate) || normalizedCandidate.contains(value))) return 0.35D;
        }
        return 0D;
    }

    private static double yearScore(Integer sourceYear, Integer candidateYear) {
        if (sourceYear == null || candidateYear == null) return 0.05D;
        int difference = Math.abs(sourceYear - candidateYear);
        if (difference == 0) return 0.25D;
        return difference == 1 ? 0.15D : 0D;
    }

    static PosterAsset choosePoster(List<PosterAsset> posters) {
        return posters.stream()
                .filter(poster -> poster.filePath() != null && !poster.filePath().isBlank())
                .min(Comparator.comparingInt((PosterAsset poster) -> languageRank(poster.language()))
                        .thenComparing(Comparator.comparingDouble(PosterAsset::voteAverage).reversed())
                        .thenComparing(Comparator.comparingInt(PosterAsset::voteCount).reversed()))
                .orElse(null);
    }

    private static int languageRank(String language) {
        if ("zh".equalsIgnoreCase(language)) return 0;
        if ("en".equalsIgnoreCase(language)) return 1;
        if (language == null || language.isBlank()) return 2;
        return 3;
    }

    static String normalizeTitle(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[（(]?(?:19|20)\\d{2}[）)]?", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Map<String, Object> diagnostics(ScoredCandidate best, Integer sourceYear) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("candidateId", best.candidate().id());
        values.put("candidateTitle", best.candidate().title() == null ? "unknown" : best.candidate().title());
        values.put("sourceYear", sourceYear == null ? "unknown" : sourceYear);
        values.put("candidateYear", best.candidate().year() == null ? "unknown" : best.candidate().year());
        values.put("titleScore", best.titleScore());
        values.put("yearScore", best.yearScore());
        values.put("typeScore", best.typeScore());
        values.put("threshold", AUTO_ACCEPT_THRESHOLD);
        return Map.copyOf(values);
    }

    private static MatchResult result(Status status, SearchCandidate candidate, PosterAsset poster,
                                      ImageConfiguration configuration, BigDecimal confidence,
                                      Map<String, Object> diagnostics) {
        return new MatchResult(status, candidate, poster, configuration,
                confidence.setScale(4, RoundingMode.HALF_UP), diagnostics);
    }

    public interface Gateway {
        List<SearchCandidate> search(MediaType mediaType, String query, Integer year, Credential credential);
        List<PosterAsset> posters(MediaType mediaType, long tmdbId, Credential credential);
        ImageConfiguration configuration(Credential credential);
    }

    public record Credential(CredentialType type, String value) {
        public Credential {
            if (type == null) throw new IllegalArgumentException("TMDB credential type is required");
            if (value == null || value.isBlank()) throw new IllegalArgumentException("TMDB credential is required");
            value = value.trim();
        }

        @Override
        public String toString() {
            return "Credential[type=" + type + ", value=REDACTED]";
        }
    }

    public enum CredentialType { API_KEY, READ_ACCESS_TOKEN }

    public enum MediaType {
        MOVIE("movie"), TV("tv");

        private final String apiValue;

        MediaType(String apiValue) {
            this.apiValue = apiValue;
        }

        public String apiValue() {
            return apiValue;
        }

        static MediaType forContent(ContentType type) {
            return type == ContentType.MOVIE ? MOVIE : TV;
        }
    }

    public enum Status { PENDING, ACCEPTED, NOT_FOUND, ERROR }

    public record MatchRequest(ContentType contentType, String title, List<String> aliases, Integer year) {
        public MatchRequest {
            if (contentType == null) throw new IllegalArgumentException("Content type is required");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    public record SearchCandidate(long id, MediaType mediaType, String title, String originalTitle,
                                  Integer year, String posterPath) { }

    public record PosterAsset(String filePath, String language, double voteAverage, int voteCount) { }

    public record ImageConfiguration(String secureBaseUrl, List<String> posterSizes) {
        public String preferredPosterSize() {
            if (posterSizes.contains("w500")) return "w500";
            if (posterSizes.contains("w342")) return "w342";
            return posterSizes.contains("original") ? "original" : posterSizes.stream().findFirst().orElse("original");
        }

        public String imageUrl(String posterPath) {
            if (posterPath == null || posterPath.isBlank()) return null;
            String base = secureBaseUrl.endsWith("/") ? secureBaseUrl : secureBaseUrl + "/";
            String path = posterPath.startsWith("/") ? posterPath.substring(1) : posterPath;
            return base + preferredPosterSize() + "/" + path;
        }
    }

    public record MatchResult(Status status, SearchCandidate candidate, PosterAsset poster,
                              ImageConfiguration imageConfiguration, BigDecimal confidence,
                              Map<String, Object> diagnostics) {
        public String posterUrl() {
            return poster == null || imageConfiguration == null ? null : imageConfiguration.imageUrl(poster.filePath());
        }
    }

    private record ScoredCandidate(SearchCandidate candidate, BigDecimal score,
                                   double titleScore, double yearScore, double typeScore) { }
}
