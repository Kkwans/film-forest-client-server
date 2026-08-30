package com.filmforest.content.service.impl;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.dto.ProfileOverviewItemView;
import com.filmforest.content.dto.ProfileOverviewView;
import com.filmforest.content.repository.ProfileOverviewRepository;
import com.filmforest.content.service.ProfileOverviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the profile view from one bounded item query and one stats query. */
@Service
public class ProfileOverviewServiceImpl implements ProfileOverviewService {

    public static final int AGGREGATE_LIMIT = 200;
    private static final int PREVIEW_LIMIT = 6;
    private static final int FACET_LIMIT = 5;

    private final ProfileOverviewRepository repository;

    public ProfileOverviewServiceImpl(ProfileOverviewRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileOverviewView getOverview(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(401, "未登录，请先登录");
        }

        ProfileOverviewRepository.StatsRow stats = repository.findStats(userId);
        List<ProfileOverviewRepository.ItemRow> rows = repository.findVisibleItems(userId, AGGREGATE_LIMIT);
        List<ProfileOverviewItemView> items = rows == null ? List.of() : rows.stream().map(this::toView).toList();

        List<ProfileOverviewItemView> recentWatched = items.stream()
                .filter(item -> "watched".equals(item.getListType()))
                .sorted(Comparator.comparing(ProfileOverviewItemView::getWatchedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProfileOverviewItemView::getId, Comparator.reverseOrder()))
                .limit(PREVIEW_LIMIT)
                .toList();
        List<ProfileOverviewItemView> recentRatings = items.stream()
                .filter(item -> "watched".equals(item.getListType()) && item.getUserRating() != null)
                .limit(PREVIEW_LIMIT)
                .toList();
        List<ProfileOverviewItemView> wantPreview = items.stream()
                .filter(item -> "want_to_watch".equals(item.getListType()))
                .limit(PREVIEW_LIMIT)
                .toList();
        List<ProfileOverviewItemView> customPreview = items.stream()
                .filter(item -> "custom".equals(item.getListType()))
                .limit(PREVIEW_LIMIT)
                .toList();

        return new ProfileOverviewView(
                new ProfileOverviewView.Stats(stats == null ? 0 : stats.listCount(),
                        stats == null ? 0 : stats.wantCount(),
                        stats == null ? 0 : stats.watchedCount(),
                        stats == null ? 0 : stats.customCount()),
                recentWatched,
                recentRatings,
                wantPreview,
                customPreview,
                facets(items, true),
                facets(items, false));
    }

    private ProfileOverviewItemView toView(ProfileOverviewRepository.ItemRow row) {
        ProfileOverviewItemView view = new ProfileOverviewItemView();
        view.setId(row.id());
        view.setListId(row.listId());
        view.setListName(row.listName());
        view.setListType(row.listType());
        view.setMovieId(row.movieId());
        view.setContentType(row.contentType());
        view.setAddedAt(toUtcOffset(row.addedAt()));
        view.setWatchedAt(toUtcOffset(row.watchedAt()));
        view.setTitle(row.title());
        view.setCover(row.cover());
        view.setYear(row.year());
        view.setRating(row.rating());
        view.setUserRating(row.userRating());
        view.setNote(row.note());
        view.setRegion(row.region());
        view.setGenre(row.genre());
        view.setDuration(row.duration());
        view.setTotalEpisode(row.totalEpisode());
        return view;
    }

    private List<ProfileOverviewView.Facet> facets(List<ProfileOverviewItemView> items, boolean genre) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ProfileOverviewItemView item : items) {
            for (String value : parseArray(genre ? item.getGenre() : item.getRegion())) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(FACET_LIMIT)
                .map(entry -> new ProfileOverviewView.Facet(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<String> parseArray(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : value.split(",|/")) {
            String normalized = part.trim().replaceAll("^\"|\"$", "").trim();
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result;
    }

    private static OffsetDateTime toUtcOffset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
