package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.filmforest.content.dto.RelatedVO;
import com.filmforest.content.entity.ContentTag;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.mapper.AnimeMapper;
import com.filmforest.content.mapper.ContentTagMapper;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.mapper.ShortDramaMapper;
import com.filmforest.content.mapper.VarietyMapper;
import com.filmforest.content.service.impl.RelatedServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatedServiceImplTest {

    @Mock private MovieMapper movieMapper;
    @Mock private DramaMapper dramaMapper;
    @Mock private AnimeMapper animeMapper;
    @Mock private VarietyMapper varietyMapper;
    @Mock private ShortDramaMapper shortDramaMapper;
    @Mock private ContentTagMapper contentTagMapper;

    @Test
    void standardGenreOverlapControlsRecommendationOrder() {
        RelatedServiceImpl service = service();
        Movie current = movie(1L, "当前电影", 7.0);
        current.setGenre("[\"旧兼容题材\"]");
        Movie first = movie(2L, "两个标准题材重合", null);
        Movie second = movie(3L, "一个标准题材重合", 9.0);

        when(movieMapper.selectOne(any(QueryWrapper.class))).thenReturn(current);
        when(contentTagMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(tag(null, 10L), tag(null, 20L)))
                .thenReturn(List.of(tag(2L, null), tag(3L, null)));
        // 数据库内容查询顺序不应覆盖 content_tag 聚合得到的相关度顺序。
        when(movieMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(second, first));

        List<RelatedVO> related = service.getRelated("movie", 1L, 2);

        assertThat(related).extracting(RelatedVO::getId).containsExactly(2L, 3L);
        assertThat(related.get(0).getScoreDouban()).isNull();
    }

    @Test
    void routeAliasAndUnsupportedTypeAreHandledSafely() {
        RelatedServiceImpl service = service();

        assertThat(service.getRelated("short", 1L, 6)).isEmpty();
        assertThat(service.getRelated("unknown", 1L, 6)).isEmpty();
    }

    @Test
    void cardProjectionIsCompleteAcrossAllContentTypes() {
        RelatedServiceImpl service = service();

        Movie movie = movie(2L, "电影", 8.1);
        movie.setGenre("[\"剧情\"]");
        movie.setDuration(128);
        when(movieMapper.selectOne(any(QueryWrapper.class))).thenReturn(movie(1L, "当前电影", 7.0));
        when(movieMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(movie));

        Drama drama = drama(2L, "剧集");
        when(dramaMapper.selectOne(any(QueryWrapper.class))).thenReturn(drama(1L, "当前剧集"));
        when(dramaMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(drama));

        Anime anime = anime(2L, "动漫");
        when(animeMapper.selectOne(any(QueryWrapper.class))).thenReturn(anime(1L, "当前动漫"));
        when(animeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(anime));

        Variety variety = variety(2L, "综艺");
        when(varietyMapper.selectOne(any(QueryWrapper.class))).thenReturn(variety(1L, "当前综艺"));
        when(varietyMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(variety));

        ShortDrama shortDrama = shortDrama(2L, "短剧");
        when(shortDramaMapper.selectOne(any(QueryWrapper.class))).thenReturn(shortDrama(1L, "当前短剧"));
        when(shortDramaMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(shortDrama));

        assertCardProjection(service.getRelated("movie", 1L, 1).get(0), "movie", 128, null);
        assertCardProjection(service.getRelated("drama", 1L, 1).get(0), "drama", 45, 24);
        assertCardProjection(service.getRelated("anime", 1L, 1).get(0), "anime", 25, 12);
        assertCardProjection(service.getRelated("variety", 1L, 1).get(0), "variety", 90, 16);
        assertCardProjection(service.getRelated("short_drama", 1L, 1).get(0), "short_drama", 3, 80);
    }

    private RelatedServiceImpl service() {
        return new RelatedServiceImpl(movieMapper, dramaMapper, animeMapper, varietyMapper,
                shortDramaMapper, contentTagMapper);
    }

    private Movie movie(long id, String title, Double score) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        movie.setPosterUrl("https://example.com/" + id + ".jpg");
        movie.setYear(2024);
        movie.setRegion("[\"美国\"]");
        movie.setScoreDouban(score == null ? null : BigDecimal.valueOf(score));
        movie.setStatus(1);
        return movie;
    }

    private Drama drama(long id, String title) {
        Drama item = new Drama();
        populateSeries(item, id, title, 45, 24);
        return item;
    }

    private Anime anime(long id, String title) {
        Anime item = new Anime();
        populateSeries(item, id, title, 25, 12);
        return item;
    }

    private Variety variety(long id, String title) {
        Variety item = new Variety();
        populateSeries(item, id, title, 90, 16);
        return item;
    }

    private ShortDrama shortDrama(long id, String title) {
        ShortDrama item = new ShortDrama();
        populateSeries(item, id, title, 3, 80);
        return item;
    }

    private void populateSeries(Object item, long id, String title, int duration, int totalEpisode) {
        if (item instanceof Drama value) {
            value.setId(id); value.setTitle(title); value.setPosterUrl("https://example.com/" + id + ".jpg");
            value.setYear(2024); value.setRegion("[\"中国大陆\"]"); value.setGenre("[\"剧情\"]");
            value.setDuration(duration); value.setTotalEpisode(totalEpisode); value.setScoreDouban(BigDecimal.valueOf(8.0)); value.setStatus(1);
        } else if (item instanceof Anime value) {
            value.setId(id); value.setTitle(title); value.setPosterUrl("https://example.com/" + id + ".jpg");
            value.setYear(2024); value.setRegion("[\"日本\"]"); value.setGenre("[\"动画\"]");
            value.setDuration(duration); value.setTotalEpisode(totalEpisode); value.setScoreDouban(BigDecimal.valueOf(8.0)); value.setStatus(1);
        } else if (item instanceof Variety value) {
            value.setId(id); value.setTitle(title); value.setPosterUrl("https://example.com/" + id + ".jpg");
            value.setYear(2024); value.setRegion("[\"中国大陆\"]"); value.setGenre("[\"真人秀\"]");
            value.setDuration(duration); value.setTotalEpisode(totalEpisode); value.setScoreDouban(BigDecimal.valueOf(8.0)); value.setStatus(1);
        } else if (item instanceof ShortDrama value) {
            value.setId(id); value.setTitle(title); value.setPosterUrl("https://example.com/" + id + ".jpg");
            value.setYear(2024); value.setRegion("[\"中国大陆\"]"); value.setGenre("[\"短剧\"]");
            value.setDuration(duration); value.setTotalEpisode(totalEpisode); value.setScoreDouban(BigDecimal.valueOf(8.0)); value.setStatus(1);
        }
    }

    private void assertCardProjection(RelatedVO item, String type, Integer duration, Integer totalEpisode) {
        assertThat(item.getType()).isEqualTo(type);
        assertThat(item.getRegion()).isNotEmpty();
        assertThat(item.getGenre()).isNotEmpty();
        assertThat(item.getDuration()).isEqualTo(duration);
        assertThat(item.getTotalEpisode()).isEqualTo(totalEpisode);
    }

    private ContentTag tag(Long contentId, Long tagId) {
        ContentTag tag = new ContentTag();
        tag.setContentId(contentId);
        tag.setTagId(tagId);
        tag.setContentType("movie");
        return tag;
    }
}
