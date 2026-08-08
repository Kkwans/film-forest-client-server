package com.filmforest.content.poster;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.util.List;

@Component
public class PosterBatchContentSource {

    private final MovieMapper movieMapper;
    private final DramaMapper dramaMapper;
    private final VarietyMapper varietyMapper;
    private final AnimeMapper animeMapper;
    private final ShortDramaMapper shortDramaMapper;

    public PosterBatchContentSource(MovieMapper movieMapper, DramaMapper dramaMapper,
                                    VarietyMapper varietyMapper, AnimeMapper animeMapper,
                                    ShortDramaMapper shortDramaMapper) {
        this.movieMapper = movieMapper;
        this.dramaMapper = dramaMapper;
        this.varietyMapper = varietyMapper;
        this.animeMapper = animeMapper;
        this.shortDramaMapper = shortDramaMapper;
    }

    public long count(ContentType type) {
        return switch (type) {
            case MOVIE -> movieMapper.selectCount(null);
            case DRAMA -> dramaMapper.selectCount(null);
            case VARIETY -> varietyMapper.selectCount(null);
            case ANIME -> animeMapper.selectCount(null);
            case SHORT_DRAMA -> shortDramaMapper.selectCount(null);
        };
    }

    public List<Long> nextIds(ContentType type, long afterId, int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        return switch (type) {
            case MOVIE -> movieMapper.selectList(new LambdaQueryWrapper<Movie>()
                    .select(Movie::getId).gt(Movie::getId, afterId).orderByAsc(Movie::getId)
                    .last("LIMIT " + safeLimit)).stream().map(Movie::getId).toList();
            case DRAMA -> dramaMapper.selectList(new LambdaQueryWrapper<Drama>()
                    .select(Drama::getId).gt(Drama::getId, afterId).orderByAsc(Drama::getId)
                    .last("LIMIT " + safeLimit)).stream().map(Drama::getId).toList();
            case VARIETY -> varietyMapper.selectList(new LambdaQueryWrapper<Variety>()
                    .select(Variety::getId).gt(Variety::getId, afterId).orderByAsc(Variety::getId)
                    .last("LIMIT " + safeLimit)).stream().map(Variety::getId).toList();
            case ANIME -> animeMapper.selectList(new LambdaQueryWrapper<Anime>()
                    .select(Anime::getId).gt(Anime::getId, afterId).orderByAsc(Anime::getId)
                    .last("LIMIT " + safeLimit)).stream().map(Anime::getId).toList();
            case SHORT_DRAMA -> shortDramaMapper.selectList(new LambdaQueryWrapper<ShortDrama>()
                    .select(ShortDrama::getId).gt(ShortDrama::getId, afterId).orderByAsc(ShortDrama::getId)
                    .last("LIMIT " + safeLimit)).stream().map(ShortDrama::getId).toList();
        };
    }
}
