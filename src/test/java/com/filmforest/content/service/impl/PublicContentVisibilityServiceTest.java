package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.filmforest.content.model.ContentStatus;
import com.filmforest.content.service.ContentTagLookupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublicContentVisibilityServiceTest {

    private final ContentTagLookupService tags = mock(ContentTagLookupService.class);

    @Test
    void everyPublicCollectionFiltersPublishedContent() {
        MovieMapper movieMapper = mock(MovieMapper.class);
        initialize(Movie.class);
        MovieServiceImpl movies = attach(new MovieServiceImpl(tags), movieMapper);
        assertPublishedPage(movieMapper,
                () -> movies.pageList(1, 20, null, null, null, null,
                        null, null, null, "desc"));

        DramaMapper dramaMapper = mock(DramaMapper.class);
        initialize(Drama.class);
        DramaServiceImpl dramas = attach(new DramaServiceImpl(tags), dramaMapper);
        assertPublishedPage(dramaMapper,
                () -> dramas.pageList(1, 20, null, null, null, null,
                        null, null, null, "desc"));

        VarietyMapper varietyMapper = mock(VarietyMapper.class);
        initialize(Variety.class);
        VarietyServiceImpl varieties = attach(new VarietyServiceImpl(tags), varietyMapper);
        assertPublishedPage(varietyMapper,
                () -> varieties.pageList(1, 20, null, null, null, null,
                        null, null, null, "desc"));

        AnimeMapper animeMapper = mock(AnimeMapper.class);
        initialize(Anime.class);
        AnimeServiceImpl animes = attach(new AnimeServiceImpl(tags), animeMapper);
        assertPublishedPage(animeMapper,
                () -> animes.pageList(1, 20, null, null, null, null,
                        null, null, null, "desc"));

        ShortDramaMapper shortDramaMapper = mock(ShortDramaMapper.class);
        initialize(ShortDrama.class);
        ShortDramaServiceImpl shortDramas = attach(new ShortDramaServiceImpl(tags), shortDramaMapper);
        assertPublishedPage(shortDramaMapper,
                () -> shortDramas.pageList(1, 20, null, null, null, null,
                        null, null, null, "desc"));
    }

    @Test
    void everyPublicDetailFiltersPublishedContent() {
        MovieMapper movieMapper = mock(MovieMapper.class);
        initialize(Movie.class);
        MovieServiceImpl movies = attach(new MovieServiceImpl(tags), movieMapper);
        assertPublishedDetail(movieMapper, () -> movies.getDetail(7L));

        DramaMapper dramaMapper = mock(DramaMapper.class);
        initialize(Drama.class);
        DramaServiceImpl dramas = attach(new DramaServiceImpl(tags), dramaMapper);
        assertPublishedDetail(dramaMapper, () -> dramas.getDetail(7L));

        VarietyMapper varietyMapper = mock(VarietyMapper.class);
        initialize(Variety.class);
        VarietyServiceImpl varieties = attach(new VarietyServiceImpl(tags), varietyMapper);
        assertPublishedDetail(varietyMapper, () -> varieties.getDetail(7L));

        AnimeMapper animeMapper = mock(AnimeMapper.class);
        initialize(Anime.class);
        AnimeServiceImpl animes = attach(new AnimeServiceImpl(tags), animeMapper);
        assertPublishedDetail(animeMapper, () -> animes.getDetail(7L));

        ShortDramaMapper shortDramaMapper = mock(ShortDramaMapper.class);
        initialize(ShortDrama.class);
        ShortDramaServiceImpl shortDramas = attach(new ShortDramaServiceImpl(tags), shortDramaMapper);
        assertPublishedDetail(shortDramaMapper, () -> shortDramas.getDetail(7L));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T, S> S attach(S service, BaseMapper<T> mapper) {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private void initialize(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void assertPublishedPage(BaseMapper<T> mapper, Runnable query) {
        doAnswer(invocation -> invocation.getArgument(0))
                .when(mapper).selectPage(any(IPage.class), any(Wrapper.class));

        query.run();

        ArgumentCaptor<Wrapper<T>> wrapper = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(IPage.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("status");
        assertThat(((AbstractWrapper<?, ?, ?>) wrapper.getValue()).getParamNameValuePairs())
                .containsValue(ContentStatus.PUBLISHED.code());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void assertPublishedDetail(BaseMapper<T> mapper, Runnable query) {
        query.run();

        ArgumentCaptor<Wrapper<T>> wrapper = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(wrapper.capture(), org.mockito.ArgumentMatchers.eq(true));
        assertThat(wrapper.getValue().getSqlSegment()).contains("id", "status");
        assertThat(((AbstractWrapper<?, ?, ?>) wrapper.getValue()).getParamNameValuePairs())
                .containsValues(7L, ContentStatus.PUBLISHED.code());
    }
}
