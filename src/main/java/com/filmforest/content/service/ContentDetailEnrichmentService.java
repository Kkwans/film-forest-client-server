package com.filmforest.content.service;

import com.filmforest.content.entity.Anime;
import com.filmforest.content.entity.ContentPosterMatch;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.ContentPosterMatchService;
import org.springframework.stereotype.Service;

/**
 * Adds optional TMDB metadata to a detail projection without mixing it into source ratings.
 * The content entities keep these fields transient because the values live in the match table.
 */
@Service
public class ContentDetailEnrichmentService {

    private final ContentPosterMatchService matchService;

    public ContentDetailEnrichmentService(ContentPosterMatchService matchService) {
        this.matchService = matchService;
    }

    public Movie enrich(ContentType type, Movie content) {
        if (content == null) return null;
        apply(type, content.getId(), content::setTmdbScore, content::setTmdbVoteCount);
        return content;
    }

    public Drama enrich(ContentType type, Drama content) {
        if (content == null) return null;
        apply(type, content.getId(), content::setTmdbScore, content::setTmdbVoteCount);
        return content;
    }

    public Variety enrich(ContentType type, Variety content) {
        if (content == null) return null;
        apply(type, content.getId(), content::setTmdbScore, content::setTmdbVoteCount);
        return content;
    }

    public Anime enrich(ContentType type, Anime content) {
        if (content == null) return null;
        apply(type, content.getId(), content::setTmdbScore, content::setTmdbVoteCount);
        return content;
    }

    public ShortDrama enrich(ContentType type, ShortDrama content) {
        if (content == null) return null;
        apply(type, content.getId(), content::setTmdbScore, content::setTmdbVoteCount);
        return content;
    }

    private void apply(ContentType type, Long contentId,
                       java.util.function.Consumer<java.math.BigDecimal> scoreSetter,
                       java.util.function.Consumer<Integer> voteCountSetter) {
        if (type == null || contentId == null) return;
        ContentPosterMatch match = matchService.find(type, contentId);
        if (match == null) return;
        scoreSetter.accept(match.getTmdbScore());
        voteCountSetter.accept(match.getTmdbVoteCount());
    }
}
