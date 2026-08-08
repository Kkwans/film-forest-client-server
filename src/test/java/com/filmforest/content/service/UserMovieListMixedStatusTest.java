package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.filmforest.content.dto.ContentStatusQuery;
import com.filmforest.content.entity.UserMovieList;
import com.filmforest.content.entity.UserMovieListItem;
import com.filmforest.content.mapper.UserMovieListItemMapper;
import com.filmforest.content.mapper.UserMovieListMapper;
import com.filmforest.content.service.impl.UserMovieListServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMovieListMixedStatusTest {

    @Mock private UserMovieListMapper listMapper;
    @Mock private UserMovieListItemMapper itemMapper;
    private UserMovieListServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "mixed-status-test"),
                UserMovieListItem.class);
        service = new UserMovieListServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", listMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
    }

    @Test
    void batchesDifferentTypesWithoutIdCollisionOrPerItemQueries() {
        UserMovieList watched = new UserMovieList();
        watched.setId(9L);
        watched.setName("看过");
        watched.setType("watched");
        watched.setIsDefault(1);

        UserMovieListItem movieItem = new UserMovieListItem();
        movieItem.setListId(9L);
        movieItem.setMovieId(7L);
        movieItem.setContentType("movie");

        when(listMapper.selectList(any(Wrapper.class))).thenReturn(List.of(watched));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(movieItem));

        var result = service.getContentStatusBatch(42L, List.of(
                new ContentStatusQuery("movie", 7L),
                new ContentStatusQuery("short", 7L)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).contentType()).isEqualTo("movie");
        assertThat(result.get(0).statuses()).singleElement()
                .satisfies(status -> assertThat(status.get("added")).isEqualTo(true));
        assertThat(result.get(1).contentType()).isEqualTo("short_drama");
        assertThat(result.get(1).statuses()).singleElement()
                .satisfies(status -> assertThat(status.get("added")).isEqualTo(false));
        verify(itemMapper).selectList(any(Wrapper.class));
    }
}
