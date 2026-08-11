package com.filmforest.content.service;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.dto.UserPlaybackHistoryRequest;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.UserPlaybackHistory;
import com.filmforest.content.mapper.AnimeMapper;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.mapper.ShortDramaMapper;
import com.filmforest.content.mapper.UserPlaybackHistoryMapper;
import com.filmforest.content.mapper.VarietyMapper;
import com.filmforest.content.service.impl.UserPlaybackHistoryServiceImpl;
import com.filmforest.resource.service.ResourceOnlineService;
import com.filmforest.resource.entity.ResourceOnline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPlaybackHistoryServiceTest {

    @Mock private UserPlaybackHistoryMapper historyMapper;
    @Mock private PublishedContentAccessService publishedContentAccessService;
    @Mock private ResourceOnlineService resourceOnlineService;
    @Mock private MovieMapper movieMapper;
    @Mock private DramaMapper dramaMapper;
    @Mock private VarietyMapper varietyMapper;
    @Mock private AnimeMapper animeMapper;
    @Mock private ShortDramaMapper shortDramaMapper;

    private UserPlaybackHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserPlaybackHistoryServiceImpl(
                historyMapper, publishedContentAccessService, resourceOnlineService,
                movieMapper, dramaMapper, varietyMapper, animeMapper, shortDramaMapper);
    }

    @Test
    void upsertNormalizesShortAndDerivesResourceFields() {
        UserPlaybackHistoryRequest request = request(" short ", 7L, 12L, 90L, true);
        request.setResourceId(99L);
        when(publishedContentAccessService.isPublished("short_drama", 7L)).thenReturn(true);
        ResourceOnline resource = resource(99L, "short_drama", 7L);
        resource.setEpisodeNumber(3);
        resource.setEpisodeTitle("第三集");
        resource.setSourceName("主源");
        resource.setPlaybackType("HLS");
        when(resourceOnlineService.getById(99L)).thenReturn(resource);

        service.upsert(42L, request);

        ArgumentCaptor<UserPlaybackHistory> captor = ArgumentCaptor.forClass(UserPlaybackHistory.class);
        verify(historyMapper).upsert(captor.capture());
        UserPlaybackHistory saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getContentType()).isEqualTo("short_drama");
        assertThat(saved.getContentId()).isEqualTo(7L);
        assertThat(saved.getResourceOnlineId()).isEqualTo(99L);
        assertThat(saved.getEpisodeNumber()).isEqualTo(3);
        assertThat(saved.getEpisodeTitle()).isEqualTo("第三集");
        assertThat(saved.getSourceName()).isEqualTo("主源");
        assertThat(saved.getPlaybackType()).isEqualTo("HLS");
        assertThat(saved.getCompleted()).isTrue();
        assertThat(saved.getLastPlayedAt()).isNull();
    }

    @Test
    void unpublishedContentCannotBeRecorded() {
        when(publishedContentAccessService.isPublished("movie", 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.upsert(42L, request("movie", 7L, 0L, null, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未上线");
        verify(historyMapper, never()).upsert(any());
    }

    @Test
    void mismatchedResourceCannotBeRecorded() {
        when(publishedContentAccessService.isPublished("movie", 7L)).thenReturn(true);
        when(resourceOnlineService.getById(99L)).thenReturn(resource(99L, "drama", 7L));
        UserPlaybackHistoryRequest request = request("movie", 7L, 1L, 10L, false);
        request.setResourceId(99L);

        assertThatThrownBy(() -> service.upsert(42L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不匹配");
        verify(historyMapper, never()).upsert(any());
    }

    @Test
    void positionBoundariesAreEnforced() {
        when(publishedContentAccessService.isPublished("movie", 7L)).thenReturn(true);

        service.upsert(42L, request("movie", 7L, 604800L, null, true));
        verify(historyMapper).upsert(any());

        UserPlaybackHistoryRequest overLimit = request("movie", 7L, 604801L, null, true);
        assertThatThrownBy(() -> service.upsert(42L, overLimit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("604800");
    }

    @Test
    void listExcludesOfflineContentAndClearsInvalidResourceFields() {
        UserPlaybackHistory visible = history(1L, 42L, "movie", 7L, 99L);
        UserPlaybackHistory offline = history(2L, 42L, "movie", 8L, null);
        when(historyMapper.selectByUserOrderByLastPlayed(42L, 20)).thenReturn(List.of(visible, offline));
        when(publishedContentAccessService.isPublished("movie", 7L)).thenReturn(true);
        when(publishedContentAccessService.isPublished("movie", 8L)).thenReturn(false);
        when(movieMapper.selectById(7L)).thenReturn(movie(7L, 1, 0));
        ResourceOnline deleted = resource(99L, "movie", 7L);
        deleted.setDeleted(1);
        when(resourceOnlineService.getById(99L)).thenReturn(deleted);

        var result = service.list(42L, 20);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.getContentId()).isEqualTo(7L);
            assertThat(view.getTitle()).isEqualTo("电影");
            assertThat(view.getResourceId()).isNull();
            assertThat(view.getEpisodeNumber()).isNull();
            assertThat(view.getPositionSeconds()).isEqualTo(12L);
        });
    }

    @Test
    void removeAndClearAreScopedToAuthenticatedUser() {
        service.remove(42L, "short", 7L);
        service.clear(42L);

        verify(historyMapper).deleteByUserAndContent(42L, "short_drama", 7L);
        verify(historyMapper).deleteByUserId(42L);
    }

    @Test
    void missingAuthenticationAndInvalidLimitAreRejectedByService() {
        assertThatThrownBy(() -> service.list(null, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
        assertThatThrownBy(() -> service.list(42L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit");
        verify(historyMapper, never()).selectByUserOrderByLastPlayed(eq(42L), eq(0));
    }

    private UserPlaybackHistoryRequest request(String contentType, Long contentId,
                                               Long position, Long duration, Boolean completed) {
        UserPlaybackHistoryRequest request = new UserPlaybackHistoryRequest();
        request.setContentType(contentType);
        request.setContentId(contentId);
        request.setPositionSeconds(position);
        request.setDurationSeconds(duration);
        request.setCompleted(completed);
        return request;
    }

    private UserPlaybackHistory history(long id, long userId, String contentType,
                                        long contentId, Long resourceId) {
        UserPlaybackHistory history = new UserPlaybackHistory();
        history.setId(id);
        history.setUserId(userId);
        history.setContentType(contentType);
        history.setContentId(contentId);
        history.setResourceOnlineId(resourceId);
        history.setPositionSeconds(12L);
        history.setDurationSeconds(120L);
        history.setCompleted(false);
        history.setLastPlayedAt(LocalDateTime.now());
        return history;
    }

    private Movie movie(long id, int status, int deleted) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle("电影");
        movie.setPosterUrl("/poster.jpg");
        movie.setYear(2026);
        movie.setStatus(status);
        movie.setDeleted(deleted);
        return movie;
    }

    private ResourceOnline resource(long id, String contentType, long contentId) {
        ResourceOnline resource = new ResourceOnline();
        resource.setId(id);
        resource.setContentType(contentType);
        resource.setContentId(contentId);
        resource.setDeleted(0);
        return resource;
    }
}
