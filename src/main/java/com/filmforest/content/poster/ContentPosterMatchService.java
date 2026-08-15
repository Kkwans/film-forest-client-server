package com.filmforest.content.poster;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.mapper.ContentPosterMatchMapper;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher.MatchResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ContentPosterMatchService {

    private static final String FALLBACK_IMAGE_BASE = "https://image.tmdb.org/t/p/";
    private static final Set<String> ALLOWED_IMAGE_HOSTS = Set.of("image.tmdb.org", "media.themoviedb.org");

    private final ContentPosterMatchMapper mapper;
    private final ObjectMapper objectMapper;

    public ContentPosterMatchService(ContentPosterMatchMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public ContentPosterMatch find(ContentType contentType, long contentId) {
        return mapper.selectOne(new LambdaQueryWrapper<ContentPosterMatch>()
                .eq(ContentPosterMatch::getContentType, contentType.code())
                .eq(ContentPosterMatch::getContentId, contentId));
    }

    @Transactional
    public ContentPosterMatch save(ContentType contentType, long contentId, String sourcePosterUrl,
                                   MatchResult result) {
        ContentPosterMatch existing = find(contentType, contentId);
        if (existing != null && "accepted".equals(existing.getMatchStatus())
                && result.status() != com.filmforest.content.poster.tmdb.TmdbPosterMatcher.Status.ACCEPTED) {
            return existing;
        }
        ContentPosterMatch entity = existing == null ? new ContentPosterMatch() : existing;
        entity.setContentType(contentType.code());
        entity.setContentId(contentId);
        entity.setSourcePosterUrl(sourcePosterUrl);
        entity.setTmdbMediaType(result.candidate() == null ? null : result.candidate().mediaType().apiValue());
        entity.setTmdbId(result.candidate() == null ? null : result.candidate().id());
        entity.setTmdbScore(result.tmdbScore());
        entity.setTmdbVoteCount(result.tmdbVoteCount());
        entity.setPosterPath(result.poster() == null ? null : result.poster().filePath());
        entity.setPosterLanguage(result.poster() == null ? null : result.poster().language());
        entity.setConfidence(scale(result.confidence()));
        entity.setMatchStatus(result.status().name().toLowerCase(Locale.ROOT));
        entity.setDiagnostic(json(result.diagnostics()));
        entity.setMatchedAt(LocalDateTime.now(ZoneOffset.UTC));
        persist(entity, existing == null);
        return entity;
    }

    @Transactional
    public ContentPosterMatch saveError(ContentType contentType, long contentId, String sourcePosterUrl,
                                        String errorCode, int statusCode) {
        ContentPosterMatch existing = find(contentType, contentId);
        if (existing != null && "accepted".equals(existing.getMatchStatus())) return existing;
        ContentPosterMatch entity = existing == null ? new ContentPosterMatch() : existing;
        entity.setContentType(contentType.code());
        entity.setContentId(contentId);
        entity.setSourcePosterUrl(sourcePosterUrl);
        entity.setTmdbMediaType(null);
        entity.setTmdbId(null);
        entity.setTmdbScore(null);
        entity.setTmdbVoteCount(null);
        entity.setPosterPath(null);
        entity.setPosterLanguage(null);
        entity.setConfidence(null);
        entity.setMatchStatus("error");
        entity.setDiagnostic(json(Map.of("reason", errorCode, "statusCode", statusCode)));
        entity.setMatchedAt(LocalDateTime.now(ZoneOffset.UTC));
        persist(entity, existing == null);
        return entity;
    }

    public String acceptedPosterUrl(ContentPosterMatch match) {
        if (match == null || !"accepted".equals(match.getMatchStatus())
                || match.getPosterPath() == null || match.getPosterPath().isBlank()) return null;
        ImageAddress address = imageAddress(match.getDiagnostic());
        String path = match.getPosterPath().startsWith("/") ? match.getPosterPath().substring(1) : match.getPosterPath();
        return address.baseUrl() + address.size() + "/" + path;
    }

    public String diagnosticReason(ContentPosterMatch match) {
        if (match == null || match.getDiagnostic() == null) return null;
        try {
            JsonNode value = objectMapper.readTree(match.getDiagnostic()).path("reason");
            return value.isMissingNode() || value.isNull() ? null : value.asText();
        } catch (JsonProcessingException ignored) {
            return "invalid_diagnostic";
        }
    }

    private void persist(ContentPosterMatch entity, boolean insert) {
        if (!insert) {
            mapper.updateById(entity);
            return;
        }
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException concurrentInsert) {
            ContentPosterMatch current = find(ContentType.parse(entity.getContentType()), entity.getContentId());
            if (current == null) throw concurrentInsert;
            entity.setId(current.getId());
            mapper.updateById(entity);
        }
    }

    private ImageAddress imageAddress(String diagnostic) {
        String base = FALLBACK_IMAGE_BASE;
        String size = "w500";
        try {
            JsonNode root = objectMapper.readTree(diagnostic == null ? "{}" : diagnostic);
            String candidateBase = root.path("secureImageBaseUrl").asText("");
            String candidateSize = root.path("posterSize").asText("");
            if (allowedBase(candidateBase)) base = candidateBase.endsWith("/") ? candidateBase : candidateBase + "/";
            if (candidateSize.matches("(?:w\\d+|original)")) size = candidateSize;
        } catch (JsonProcessingException ignored) {
            // Keep the safe TMDB default for legacy rows with malformed diagnostics.
        }
        return new ImageAddress(base, size);
    }

    private boolean allowedBase(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && ALLOWED_IMAGE_HOSTS.contains(uri.getHost());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize TMDB match diagnostics", error);
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    private record ImageAddress(String baseUrl, String size) { }
}
