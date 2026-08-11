package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.filmforest.content.dto.RelatedVO;
import com.filmforest.content.entity.ContentTag;
import com.filmforest.content.entity.Movie;
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

    private ContentTag tag(Long contentId, Long tagId) {
        ContentTag tag = new ContentTag();
        tag.setContentId(contentId);
        tag.setTagId(tagId);
        tag.setContentType("movie");
        return tag;
    }
}
