package com.filmforest.content.controller;

import com.filmforest.content.model.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchControllerContractTest {

    @Test
    void typeFilterSupportsCanonicalAndRouteAliases() {
        assertThat(SearchController.parseTypeFilter("movie,short"))
                .containsExactlyInAnyOrder(ContentType.MOVIE, ContentType.SHORT_DRAMA);
        assertThat(SearchController.parseTypeFilter("all"))
                .containsExactlyInAnyOrder(ContentType.values());
    }

    @Test
    void tagIdMustBePositiveWhenProvided() {
        SearchController.validateTagId(null);
        SearchController.validateTagId(1L);

        assertThatThrownBy(() -> SearchController.validateTagId(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tagId 必须为正整数");
    }

    @Test
    void tagMatchesAreGroupedBySelectedCanonicalContentType() {
        List<Map<String, Object>> rows = List.of(
                Map.of("contentType", "movie", "contentId", 7L),
                Map.of("CONTENTTYPE", "movie", "CONTENTID", 7L),
                Map.of("contentType", "short", "contentId", 9L),
                Map.of("contentType", "drama", "contentId", 11L),
                Map.of("contentType", "unknown", "contentId", 13L),
                Map.of("contentType", "movie", "contentId", -1L)
        );

        Map<ContentType, Set<Long>> grouped = SearchController.groupTagMatches(
                rows, Set.of(ContentType.MOVIE, ContentType.SHORT_DRAMA));

        assertThat(grouped.get(ContentType.MOVIE)).containsExactly(7L);
        assertThat(grouped.get(ContentType.SHORT_DRAMA)).containsExactly(9L);
        assertThat(grouped).doesNotContainKey(ContentType.DRAMA);
    }
}
