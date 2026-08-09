package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.UserMovieList;
import com.filmforest.content.entity.UserMovieListItem;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.mapper.UserMovieListItemMapper;
import com.filmforest.content.mapper.UserMovieListMapper;
import com.filmforest.content.service.impl.UserMovieListServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMovieListVisibilityAndRatingTest {

    @Mock private UserMovieListMapper listMapper;
    @Mock private UserMovieListItemMapper itemMapper;
    @Mock private MovieMapper movieMapper;
    @Mock private PublishedContentAccessService publishedContentAccessService;
    private UserMovieListServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        initialize(UserMovieList.class, "list-visibility-list");
        initialize(UserMovieListItem.class, "list-visibility-item");
        initialize(Movie.class, "list-visibility-movie");
        service = new UserMovieListServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", listMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "movieMapper", movieMapper);
        ReflectionTestUtils.setField(service, "publishedContentAccessService", publishedContentAccessService);
    }

    @Test
    void draftOrOfflineContentCannotBeAdded() {
        UserMovieList list = list("custom");
        when(listMapper.selectById(9L)).thenReturn(list);
        when(publishedContentAccessService.isPublished("movie", 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.addItem(42L, 9L, 7L, "movie", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未上线");
        verify(itemMapper, never()).insert(any(UserMovieListItem.class));
    }

    @Test
    void ratingIsLimitedToWatchedAndHalfPointSteps() {
        when(publishedContentAccessService.isPublished("movie", 7L)).thenReturn(true);
        when(listMapper.selectById(9L)).thenReturn(list("watching"));
        assertThatThrownBy(() -> service.addItem(42L, 9L, 7L, "movie", new BigDecimal("8.5"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("看过");

        when(listMapper.selectById(9L)).thenReturn(list("watched"));
        assertThatThrownBy(() -> service.addItem(42L, 9L, 7L, "movie", new BigDecimal("8.3"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0.5");

        when(publishedContentAccessService.isPublished("short_drama", 7L)).thenReturn(true);
        service.addItem(42L, 9L, 7L, "short", new BigDecimal("8.5"), " 推荐 ");
        verify(itemMapper).insert(any(UserMovieListItem.class));
    }

    @Test
    void listPaginationAndTotalExcludeUnpublishedEntries() {
        when(listMapper.selectById(9L)).thenReturn(list("custom"));
        UserMovieListItem visible = item(1L);
        UserMovieListItem hidden = item(2L);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(visible, hidden));
        Movie movie = new Movie();
        movie.setId(1L);
        movie.setTitle("已上线");
        when(movieMapper.selectList(any(Wrapper.class))).thenReturn(List.of(movie));

        var page = service.getListItems(42L, 9L, 1, 20, "addedAt", "desc", null);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).singleElement()
                .satisfies(item -> assertThat(item.getMovieId()).isEqualTo(1L));
    }

    @Test
    void defaultListsCannotBeRenamed() {
        UserMovieList defaultList = list("watched");
        defaultList.setIsDefault(1);
        when(listMapper.selectById(9L)).thenReturn(defaultList);

        assertThatThrownBy(() -> service.updateList(42L, 9L, "新名称", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不可编辑");
    }

    private UserMovieList list(String type) {
        UserMovieList list = new UserMovieList();
        list.setId(9L);
        list.setUserId(42L);
        list.setType(type);
        list.setIsDefault(0);
        return list;
    }

    private UserMovieListItem item(long id) {
        UserMovieListItem item = new UserMovieListItem();
        item.setId(id);
        item.setListId(9L);
        item.setMovieId(id);
        item.setContentType("movie");
        return item;
    }

    private void initialize(Class<?> entityType, String namespace) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), namespace);
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
