package com.filmforest.content.poster.tmdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.filmforest.content.poster.tmdb.TmdbPosterMatcher.*;

@Component
public class TmdbApiClient implements Gateway {

    private static final URI API_BASE = URI.create("https://api.themoviedb.org/3/");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TmdbApiClient(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), objectMapper);
    }

    TmdbApiClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SearchCandidate> search(MediaType mediaType, String query, Integer year, Credential credential) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("query", query);
        parameters.put("include_adult", "false");
        parameters.put("language", "zh-CN");
        parameters.put("page", "1");
        if (year != null) {
            parameters.put(mediaType == MediaType.MOVIE ? "primary_release_year" : "first_air_date_year",
                    year.toString());
        }
        JsonNode root = request("search/" + mediaType.apiValue(), parameters, credential);
        List<SearchCandidate> candidates = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            String title = text(item, mediaType == MediaType.MOVIE ? "title" : "name");
            String originalTitle = text(item, mediaType == MediaType.MOVIE ? "original_title" : "original_name");
            String date = text(item, mediaType == MediaType.MOVIE ? "release_date" : "first_air_date");
            candidates.add(new SearchCandidate(item.path("id").asLong(), mediaType, title, originalTitle,
                    year(date), text(item, "poster_path")));
        }
        return List.copyOf(candidates);
    }

    @Override
    public List<PosterAsset> posters(MediaType mediaType, long tmdbId, Credential credential) {
        JsonNode root = request(mediaType.apiValue() + "/" + tmdbId + "/images",
                Map.of("language", "zh-CN", "include_image_language", "zh,en,null"), credential);
        List<PosterAsset> posters = new ArrayList<>();
        for (JsonNode item : root.path("posters")) {
            posters.add(new PosterAsset(text(item, "file_path"), text(item, "iso_639_1"),
                    item.path("vote_average").asDouble(), item.path("vote_count").asInt()));
        }
        return List.copyOf(posters);
    }

    @Override
    public ImageConfiguration configuration(Credential credential) {
        JsonNode images = request("configuration", Map.of(), credential).path("images");
        List<String> sizes = new ArrayList<>();
        images.path("poster_sizes").forEach(node -> sizes.add(node.asText()));
        String secureBaseUrl = images.path("secure_base_url").asText();
        if (secureBaseUrl.isBlank() || sizes.isEmpty()) throw new TmdbApiException("invalid_response", 200);
        return new ImageConfiguration(secureBaseUrl, List.copyOf(sizes));
    }

    private JsonNode request(String path, Map<String, String> parameters, Credential credential) {
        Map<String, String> query = new LinkedHashMap<>(parameters);
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();
        if (credential.type() == CredentialType.API_KEY) {
            query.put("api_key", credential.value());
        } else {
            request.header("Authorization", "Bearer " + credential.value());
        }
        request.uri(uri(path, query));
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TmdbApiException("interrupted", 0);
        } catch (IOException unavailable) {
            throw new TmdbApiException("network_error", 0);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TmdbApiException(category(response.statusCode()), response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException invalidResponse) {
            throw new TmdbApiException("invalid_response", response.statusCode());
        }
    }

    private static String category(int statusCode) {
        if (statusCode == 401 || statusCode == 403) return "authentication_failed";
        if (statusCode == 429) return "rate_limited";
        if (statusCode >= 500) return "service_unavailable";
        return "request_rejected";
    }

    private static URI uri(String path, Map<String, String> parameters) {
        StringBuilder value = new StringBuilder(API_BASE.resolve(path).toString());
        if (!parameters.isEmpty()) value.append('?');
        boolean first = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!first) value.append('&');
            first = false;
            value.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return URI.create(value.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static Integer year(String date) {
        if (date == null || date.length() < 4) return null;
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    public static final class TmdbApiException extends RuntimeException {
        private final String category;
        private final int statusCode;

        public TmdbApiException(String category, int statusCode) {
            super("TMDB request failed: category=" + category + ", status=" + statusCode);
            this.category = category;
            this.statusCode = statusCode;
        }

        public String category() {
            return category;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
