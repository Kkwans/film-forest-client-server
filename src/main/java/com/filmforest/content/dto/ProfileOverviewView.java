package com.filmforest.content.dto;

import java.util.List;

/**
 * Authenticated profile dashboard projection. Empty collections are returned
 * for users without activity so the client can render honest compact states.
 */
public record ProfileOverviewView(
        Stats stats,
        List<ProfileOverviewItemView> recentWatched,
        List<ProfileOverviewItemView> recentRatings,
        List<ProfileOverviewItemView> wantPreview,
        List<ProfileOverviewItemView> customListPreview,
        List<Facet> topGenres,
        List<Facet> topRegions) {

    public record Stats(
            long listCount,
            long wantCount,
            long watchedCount,
            long customCount) {}

    public record Facet(String value, int count) {}
}
