package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.service.impl.PersonalizedRecommendServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalizedRecommendTypeExclusionTest {

    @Mock private MovieService movieService;
    @Mock private DramaService dramaService;
    @Mock private VarietyService varietyService;
    @Mock private AnimeService animeService;
    @Mock private ShortDramaService shortDramaService;
    private PersonalizedRecommendServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "recommend-movie"), Movie.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "recommend-drama"), Drama.class);
        service = new PersonalizedRecommendServiceImpl();
        ReflectionTestUtils.setField(service, "movieService", movieService);
        ReflectionTestUtils.setField(service, "dramaService", dramaService);
        ReflectionTestUtils.setField(service, "varietyService", varietyService);
        ReflectionTestUtils.setField(service, "animeService", animeService);
        ReflectionTestUtils.setField(service, "shortDramaService", shortDramaService);
        when(movieService.list(any(Wrapper.class))).thenReturn(List.of());
        when(dramaService.list(any(Wrapper.class))).thenReturn(List.of());
        when(varietyService.list(any(Wrapper.class))).thenReturn(List.of());
        when(animeService.list(any(Wrapper.class))).thenReturn(List.of());
        when(shortDramaService.list(any(Wrapper.class))).thenReturn(List.of());
    }

    @Test
    void excludesIdsOnlyFromTheirOwnContentType() {
        service.getPersonalized(null, null, null, "movie:7,drama:8", 12);

        ArgumentCaptor<LambdaQueryWrapper<Movie>> movieQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<Drama>> dramaQuery = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(movieService).list(movieQuery.capture());
        verify(dramaService).list(dramaQuery.capture());
        movieQuery.getValue().getSqlSegment();
        dramaQuery.getValue().getSqlSegment();
        assertThat(movieQuery.getValue().getParamNameValuePairs().values()).contains(7L).doesNotContain(8L);
        assertThat(dramaQuery.getValue().getParamNameValuePairs().values()).contains(8L).doesNotContain(7L);
    }
}
