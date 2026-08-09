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
    void yearAndSortContractsAreNormalized() {
        SearchController.validateYear(null);
        SearchController.validateYear(2024);
        assertThatThrownBy(() -> SearchController.validateYear(1200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1888");

        assertThat(SearchController.normalizeSort(null)).isEqualTo("relevance");
        assertThat(SearchController.normalizeSort("latest")).isEqualTo("latest");
        assertThat(SearchController.normalizeSort("douban")).isEqualTo("rating");
        assertThat(SearchController.normalizeSort("unsupported")).isEqualTo("relevance");
    }

    @Test
    void keywordCanTargetYearAndCanonicalContentType() {
        assertThat(SearchController.parseKeywordYear("2024")).isEqualTo(2024);
        assertThat(SearchController.parseKeywordYear("24")).isNull();
        assertThat(SearchController.matchesTypeKeyword(ContentType.MOVIE, "电影")).isTrue();
        assertThat(SearchController.matchesTypeKeyword(ContentType.SHORT_DRAMA, "short")).isTrue();
        assertThat(SearchController.matchesTypeKeyword(ContentType.ANIME, "电影")).isFalse();
    }

    @Test
    void relevancePrefersExactTitleThenAliasAndGenre() {
        SearchController.SearchResult exact = result("史努比", null, null);
        SearchController.SearchResult alias = result("欢迎回家", "[\"史努比\"]", null);
        SearchController.SearchResult genre = result("森林朋友", null, "[\"史努比\"]");

        assertThat(SearchController.relevanceScore(exact, "史努比"))
                .isGreaterThan(SearchController.relevanceScore(alias, "史努比"));
        assertThat(SearchController.relevanceScore(alias, "史努比"))
                .isGreaterThan(SearchController.relevanceScore(genre, "史努比"));
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

    private SearchController.SearchResult result(String title, String alias, String genre) {
        return new SearchController.SearchResult(
                1L, "movie", title, null, 2024, 8.0, null, null,
                null, null, null, genre, null, null, null, alias, null);
    }
}
