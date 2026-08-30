package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.content.entity.UserMovieList;
import com.filmforest.content.entity.UserMovieListItem;
import com.filmforest.content.dto.UserListItemPageRow;
import com.filmforest.content.mapper.AnimeMapper;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.mapper.ShortDramaMapper;
import com.filmforest.content.mapper.VarietyMapper;
import com.filmforest.content.mapper.UserMovieListItemMapper;
import com.filmforest.content.mapper.UserMovieListMapper;
import com.filmforest.content.service.PublishedContentAccessService;
import com.filmforest.content.service.impl.UserMovieListServiceImpl;
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

class UserMovieListPagingTest {

    @Mock private UserMovieListMapper listMapper;
    @Mock private UserMovieListItemMapper itemMapper;
    @Mock private MovieMapper movieMapper;
    @Mock private DramaMapper dramaMapper;
    @Mock private VarietyMapper varietyMapper;
    @Mock private AnimeMapper animeMapper;
    @Mock private ShortDramaMapper shortDramaMapper;
    @Mock private PublishedContentAccessService publishedContentAccessService;
    private UserMovieListServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "user-list-paging-test"),
                UserMovieListItem.class);
        service = new UserMovieListServiceImpl(itemMapper, movieMapper, dramaMapper, varietyMapper,
                animeMapper, shortDramaMapper, publishedContentAccessService);
        ReflectionTestUtils.setField(service, "baseMapper", listMapper);

        UserMovieList list = new UserMovieList();
        list.setId(9L);
        list.setUserId(42L);
        when(listMapper.selectById(9L)).thenReturn(list);
    }

    @Test
    void appliesContentTypeBeforeDatabasePagination() {
        when(itemMapper.countVisible(9L, "movie")).thenReturn(1L);
        when(itemMapper.selectVisiblePage(9L, "movie", "addedAt", true, 20, 0L))
                .thenReturn(List.of());

        var result = service.getListItems(42L, 9L, 1, 20, "addedAt", "desc", "movie");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).isEmpty();
        verify(itemMapper).countVisible(9L, "movie");
        verify(itemMapper).selectVisiblePage(9L, "movie", "addedAt", true, 20, 0L);
    }

    @Test
    void sortsCompleteFilteredSetBeforePagingForContentFields() {
        UserListItemPageRow row = new UserListItemPageRow();
        row.setId(17L);
        row.setMovieId(71L);
        row.setContentType("anime");
        row.setTitle("分页结果");
        when(itemMapper.countVisible(9L, "anime")).thenReturn(41L);
        when(itemMapper.selectVisiblePage(9L, "anime", "year", true, 20, 20L))
                .thenReturn(List.of(row));

        var result = service.getListItems(42L, 9L, 2, 20, "year", "desc", "anime");

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getTotal()).isEqualTo(41L);
        assertThat(result.getRecords()).singleElement()
                .satisfies(item -> assertThat(item.getMovieId()).isEqualTo(71L));
        verify(itemMapper).selectVisiblePage(9L, "anime", "year", true, 20, 20L);
    }

    @Test
    void rejectsUnknownSortInsteadOfFallingBackToAddedAt() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                service.getListItems(42L, 9L, 1, 20, "title", "desc", null)))
                .isInstanceOf(com.filmforest.common.exception.BusinessException.class)
                .hasMessageContaining("不支持的片单排序方式");
    }
}
