package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.UserMovieList;
import com.filmforest.content.entity.UserMovieListItem;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.mapper.AnimeMapper;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.mapper.ShortDramaMapper;
import com.filmforest.content.mapper.UserMovieListItemMapper;
import com.filmforest.content.mapper.UserMovieListMapper;
import com.filmforest.content.mapper.VarietyMapper;
import com.filmforest.content.service.impl.UserMovieListServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class UserMovieListVisibilityAndRatingTest {

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
        initialize(UserMovieList.class, "list-visibility-list");
        initialize(UserMovieListItem.class, "list-visibility-item");
        initialize(Movie.class, "list-visibility-movie");
        service = new UserMovieListServiceImpl(itemMapper, movieMapper, dramaMapper, varietyMapper,
                animeMapper, shortDramaMapper, publishedContentAccessService);
        ReflectionTestUtils.setField(service, "baseMapper", listMapper);
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
        LocalDateTime addedAt = LocalDateTime.of(2026, 8, 15, 1, 2, 3);
        LocalDateTime watchedAt = LocalDateTime.of(2026, 8, 15, 1, 3, 4);
        visible.setAddedAt(addedAt);
        visible.setWatchedAt(watchedAt);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(visible, hidden));
        Movie movie = new Movie();
        movie.setId(1L);
        movie.setTitle("已上线");
        movie.setAlias("[\"别名\"]");
        movie.setWriter("[\"编剧\"]");
        movie.setDirector("[\"导演\"]");
        movie.setActor("[\"演员\"]");
        movie.setReleaseDate("2024-01-01");
        movie.setScoreDoubanCount(100);
        movie.setScoreImdbCount(200);
        movie.setScoreRtCriticCount(12);
        movie.setScoreRtAudienceCount(34);
        when(movieMapper.selectList(any(Wrapper.class))).thenReturn(List.of(movie));

        var page = service.getListItems(42L, 9L, 1, 20, "addedAt", "desc", null);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getMovieId()).isEqualTo(1L);
                    assertThat(item.getAlias()).isEqualTo("[\"别名\"]");
                    assertThat(item.getWriter()).isEqualTo("[\"编剧\"]");
                    assertThat(item.getReleaseDate()).isEqualTo("2024-01-01");
                    assertThat(item.getScoreDoubanCount()).isEqualTo(100);
                    assertThat(item.getScoreImdbCount()).isEqualTo(200);
                    assertThat(item.getScoreRtCriticCount()).isEqualTo(12);
                    assertThat(item.getScoreRtAudienceCount()).isEqualTo(34);
                    assertThat(item.getTotalEpisode()).isNull();
                    assertThat(item.getAddedAt()).isEqualTo(addedAt.atOffset(ZoneOffset.UTC));
                    assertThat(item.getWatchedAt()).isEqualTo(watchedAt.atOffset(ZoneOffset.UTC));
                });
    }

    @Test
    void watchedAtIsWrittenOnceAndRatingOrNoteEditsPreserveIt() {
        when(publishedContentAccessService.isPublished("movie", 7L)).thenReturn(true);
        when(listMapper.selectById(9L)).thenReturn(list("watched"));

        service.addItem(42L, 9L, 7L, "movie", null, null);
        ArgumentCaptor<UserMovieListItem> inserted = ArgumentCaptor.forClass(UserMovieListItem.class);
        verify(itemMapper).insert(inserted.capture());
        LocalDateTime watchedAt = inserted.getValue().getWatchedAt();
        assertThat(watchedAt).isNotNull();

        UserMovieListItem existing = item(7L);
        existing.setWatchedAt(watchedAt);
        when(itemMapper.insert(any(UserMovieListItem.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(itemMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        service.addItem(42L, 9L, 7L, "movie", new BigDecimal("8.5"), "备注");
        service.updateItem(42L, 9L, 7L, "movie", new BigDecimal("9.0"), "新备注");

        assertThat(existing.getWatchedAt()).isEqualTo(watchedAt);
        assertThat(existing.getRating()).isEqualByComparingTo("9.0");
        assertThat(existing.getNote()).isEqualTo("新备注");
        verify(itemMapper, atLeastOnce()).updateById(existing);
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
