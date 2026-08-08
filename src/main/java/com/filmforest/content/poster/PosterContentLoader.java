package com.filmforest.content.poster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.mapper.AnimeMapper;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.mapper.ShortDramaMapper;
import com.filmforest.content.mapper.VarietyMapper;
import com.filmforest.content.model.ContentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class PosterContentLoader {

    private final MovieMapper movieMapper;
    private final DramaMapper dramaMapper;
    private final VarietyMapper varietyMapper;
    private final AnimeMapper animeMapper;
    private final ShortDramaMapper shortDramaMapper;
    private final ObjectMapper objectMapper;

    public PosterContentLoader(MovieMapper movieMapper, DramaMapper dramaMapper, VarietyMapper varietyMapper,
                               AnimeMapper animeMapper, ShortDramaMapper shortDramaMapper,
                               ObjectMapper objectMapper) {
        this.movieMapper = movieMapper;
        this.dramaMapper = dramaMapper;
        this.varietyMapper = varietyMapper;
        this.animeMapper = animeMapper;
        this.shortDramaMapper = shortDramaMapper;
        this.objectMapper = objectMapper;
    }

    public ContentSnapshot load(ContentType type, long id) {
        ContentSnapshot snapshot = switch (type) {
            case MOVIE -> from(type, movieMapper.selectById(id));
            case DRAMA -> from(type, dramaMapper.selectById(id));
            case VARIETY -> from(type, varietyMapper.selectById(id));
            case ANIME -> from(type, animeMapper.selectById(id));
            case SHORT_DRAMA -> from(type, shortDramaMapper.selectById(id));
        };
        if (snapshot == null) throw new BusinessException(404, "内容不存在");
        return snapshot;
    }

    private ContentSnapshot from(ContentType type, Movie value) {
        return value == null ? null : snapshot(type, value.getId(), value.getTitle(), value.getAlias(),
                value.getYear(), value.getPosterUrl());
    }

    private ContentSnapshot from(ContentType type, Drama value) {
        return value == null ? null : snapshot(type, value.getId(), value.getTitle(), value.getAlias(),
                value.getYear(), value.getPosterUrl());
    }

    private ContentSnapshot from(ContentType type, Variety value) {
        return value == null ? null : snapshot(type, value.getId(), value.getTitle(), value.getAlias(),
                value.getYear(), value.getPosterUrl());
    }

    private ContentSnapshot from(ContentType type, Anime value) {
        return value == null ? null : snapshot(type, value.getId(), value.getTitle(), value.getAlias(),
                value.getYear(), value.getPosterUrl());
    }

    private ContentSnapshot from(ContentType type, ShortDrama value) {
        return value == null ? null : snapshot(type, value.getId(), value.getTitle(), value.getAlias(),
                value.getYear(), value.getPosterUrl());
    }

    private ContentSnapshot snapshot(ContentType type, long id, String title, String alias,
                                     Integer year, String sourcePosterUrl) {
        return new ContentSnapshot(type, id, title, aliases(alias), year, sourcePosterUrl);
    }

    private List<String> aliases(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.isArray()) {
                List<String> values = new ArrayList<>();
                root.forEach(node -> {
                    if (node.isTextual() && !node.asText().isBlank() && values.size() < 20) {
                        values.add(node.asText().trim());
                    }
                });
                return List.copyOf(values);
            }
        } catch (Exception ignored) {
            // Legacy rows may contain a plain delimited alias string.
        }
        return Arrays.stream(raw.split("[,/|、]"))
                .map(String::trim).filter(value -> !value.isBlank()).limit(20).toList();
    }

    public record ContentSnapshot(ContentType contentType, long contentId, String title,
                                  List<String> aliases, Integer year, String sourcePosterUrl) { }
}
