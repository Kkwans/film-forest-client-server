package com.filmforest.content.service;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.dto.ProfileOverviewView;
import com.filmforest.content.repository.ProfileOverviewRepository;
import com.filmforest.content.service.impl.ProfileOverviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileOverviewServiceTest {

    @Mock private ProfileOverviewRepository repository;
    private ProfileOverviewServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProfileOverviewServiceImpl(repository);
    }

    @Test
    void rejectsMissingUserBeforeQuerying() {
        assertThatThrownBy(() -> service.getOverview(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
        verifyNoInteractions(repository);
    }

    @Test
    void aggregatesBoundedPreviewsAndFacetsWithoutPerItemQueries() {
        when(repository.findStats(42L)).thenReturn(new ProfileOverviewRepository.StatsRow(8, 2, 3, 4));
        List<ProfileOverviewRepository.ItemRow> rows = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            rows.add(row(i + 1, "watched", i % 2 == 0 ? new BigDecimal("8.5") : null,
                    LocalDateTime.of(2026, 8, 20, i, 0), "[\"剧情\",\"科幻\"]", "[\"中国\"]"));
        }
        for (int i = 0; i < 2; i++) {
            rows.add(row(20 + i, "want_to_watch", null, LocalDateTime.of(2026, 8, 10, i, 0),
                    "[\"剧情\"]", "[\"中国\"]"));
        }
        for (int i = 0; i < 8; i++) {
            rows.add(row(40 + i, "custom", null, LocalDateTime.of(2026, 8, 1, i, 0),
                    "[\"喜剧\"]", "[\"美国\"]"));
        }
        when(repository.findVisibleItems(42L, ProfileOverviewServiceImpl.AGGREGATE_LIMIT)).thenReturn(rows);

        ProfileOverviewView result = service.getOverview(42L);

        assertThat(result.stats()).isEqualTo(new ProfileOverviewView.Stats(8, 2, 3, 4));
        assertThat(result.recentWatched()).hasSize(6);
        assertThat(result.recentRatings()).hasSize(4);
        assertThat(result.wantPreview()).hasSize(2);
        assertThat(result.customListPreview()).hasSize(6);
        assertThat(result.topGenres()).extracting(ProfileOverviewView.Facet::value)
                .containsExactly("剧情", "喜剧", "科幻");
        assertThat(result.topRegions()).extracting(ProfileOverviewView.Facet::value)
                .containsExactly("中国", "美国");
        verify(repository).findStats(42L);
        verify(repository).findVisibleItems(42L, ProfileOverviewServiceImpl.AGGREGATE_LIMIT);
    }

    private ProfileOverviewRepository.ItemRow row(long id, String listType, BigDecimal userRating,
                                                   LocalDateTime watchedAt, String genre, String region) {
        return new ProfileOverviewRepository.ItemRow(id, 9L, "我的片单", listType, id,
                "movie", watchedAt, watchedAt, "标题" + id, "/poster.jpg", 2026,
                new BigDecimal("7.5"), userRating, "记录", region, genre, 120, null);
    }
}
