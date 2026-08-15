package com.filmforest.content.service;

import com.filmforest.content.dto.PosterContentRef;
import com.filmforest.content.dto.PosterResolutionView;
import com.filmforest.content.dto.PosterSettingView;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.ContentPosterMatchService;
import com.filmforest.content.poster.PosterContentLoader;
import com.filmforest.content.poster.PosterContentLoader.ContentSnapshot;
import com.filmforest.content.poster.tmdb.TmdbApiClient.TmdbApiException;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher.Credential;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher.CredentialType;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher.MatchRequest;
import com.filmforest.content.poster.tmdb.TmdbPosterMatcher.MatchResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class PosterEnrichmentService {

    private static final Duration FAILURE_COOLDOWN = Duration.ofHours(6);

    private final UserPosterSettingService settingService;
    private final PosterContentLoader contentLoader;
    private final ContentPosterMatchService matchService;
    private final TmdbPosterMatcher matcher;

    public PosterEnrichmentService(UserPosterSettingService settingService,
                                   PosterContentLoader contentLoader,
                                   ContentPosterMatchService matchService,
                                   TmdbPosterMatcher matcher) {
        this.settingService = settingService;
        this.contentLoader = contentLoader;
        this.matchService = matchService;
        this.matcher = matcher;
    }

    public PosterResolutionView enrich(long userId, String rawType, long contentId) {
        ContentType contentType = ContentType.parse(rawType);
        ContentSnapshot content = contentLoader.load(contentType, contentId);
        PosterSettingView setting = settingService.get(userId);
        if (!"tmdb".equals(setting.posterSource())) {
            return original(contentType, contentId, "preference_original", null);
        }

        ContentPosterMatch existing = matchService.find(contentType, contentId);
        String acceptedUrl = matchService.acceptedPosterUrl(existing);
        if (acceptedUrl != null) return fromMatch(existing, acceptedUrl, "tmdb", null);
        if (!setting.configured()) return original(contentType, contentId, "credential_not_configured", existing);
        if (inCooldown(existing)) {
            return original(contentType, contentId, matchService.diagnosticReason(existing), existing);
        }

        UserPosterSettingService.DecryptedCredential decrypted = settingService.requireCredential(userId);
        Credential credential = new Credential(credentialType(decrypted.type()), decrypted.value());
        try {
            MatchResult result = matcher.match(new MatchRequest(contentType, content.title(), content.aliases(),
                    content.year()), credential);
            ContentPosterMatch saved = matchService.save(contentType, contentId, content.sourcePosterUrl(), result);
            String posterUrl = matchService.acceptedPosterUrl(saved);
            return posterUrl == null
                    ? original(contentType, contentId, matchService.diagnosticReason(saved), saved)
                    : fromMatch(saved, posterUrl, "tmdb", null);
        } catch (TmdbApiException error) {
            ContentPosterMatch saved = matchService.saveError(contentType, contentId, content.sourcePosterUrl(),
                    error.category(), error.statusCode());
            return original(contentType, contentId, error.category(), saved);
        }
    }

    public List<PosterResolutionView> resolve(long userId, List<PosterContentRef> items) {
        PosterSettingView setting = settingService.get(userId);
        boolean tmdb = "tmdb".equals(setting.posterSource());
        List<PosterResolutionView> results = new ArrayList<>(items.size());
        for (PosterContentRef item : items) {
            ContentType type = ContentType.parse(item.contentType());
            ContentPosterMatch match = tmdb ? matchService.find(type, item.contentId()) : null;
            String posterUrl = matchService.acceptedPosterUrl(match);
            results.add(posterUrl == null
                    ? original(type, item.contentId(), tmdb ? "not_matched" : "preference_original", match)
                    : fromMatch(match, posterUrl, "tmdb", null));
        }
        return List.copyOf(results);
    }

    private boolean inCooldown(ContentPosterMatch match) {
        if (match == null || match.getUpdatedAt() == null || "accepted".equals(match.getMatchStatus())) return false;
        return match.getUpdatedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).minus(FAILURE_COOLDOWN));
    }

    private CredentialType credentialType(String value) {
        return "api_key".equals(value) ? CredentialType.API_KEY : CredentialType.READ_ACCESS_TOKEN;
    }

    private PosterResolutionView original(ContentType type, long id, String diagnostic,
                                          ContentPosterMatch match) {
        return new PosterResolutionView(type.code(), id, null, "original",
                match == null ? "not_attempted" : match.getMatchStatus(), diagnostic,
                match == null ? null : match.getConfidence(), match == null ? null : match.getMatchedAt(),
                match == null ? null : match.getTmdbScore(),
                match == null ? null : match.getTmdbVoteCount());
    }

    private PosterResolutionView fromMatch(ContentPosterMatch match, String posterUrl, String source,
                                           String diagnostic) {
        return new PosterResolutionView(match.getContentType(), match.getContentId(), posterUrl, source,
                match.getMatchStatus(), diagnostic, match.getConfidence(), match.getMatchedAt(),
                match.getTmdbScore(), match.getTmdbVoteCount());
    }
}
