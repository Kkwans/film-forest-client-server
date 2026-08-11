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

    @Test
    void suggestionsAreRankedAcrossTypesBeforeDeduplication() {
        List<SearchController.SuggestionCandidate> candidates = List.of(
                new SearchController.SuggestionCandidate("史努比大冒险", null, "movie"),
                new SearchController.SuggestionCandidate("欢迎回家", "[\"史努比\"]", "movie"),
                new SearchController.SuggestionCandidate("史努比", null, "anime"),
                new SearchController.SuggestionCandidate("史努比", null, "drama"),
                new SearchController.SuggestionCandidate("新史努比特辑", null, "variety")
        );

        assertThat(SearchController.orderSuggestionTitles(candidates, "史努比", 10))
                .containsExactly("史努比", "欢迎回家", "史努比大冒险", "新史努比特辑");
    }

    @Test
    void suggestionLimitAndBlankCandidatesAreHandledSafely() {
        List<SearchController.SuggestionCandidate> candidates = List.of(
                new SearchController.SuggestionCandidate("", null, "movie"),
                new SearchController.SuggestionCandidate("电影甲", null, "movie"),
                new SearchController.SuggestionCandidate("电影乙", null, "drama")
        );

        assertThat(SearchController.orderSuggestionTitles(candidates, "电影", 1))
                .hasSize(1)
                .allMatch(title -> title.startsWith("电影"));
        assertThat(SearchController.orderSuggestionTitles(candidates, "电影", 0)).isEmpty();
    }

    private SearchController.SearchResult result(String title, String alias, String genre) {
        return new SearchController.SearchResult(
                1L, "movie", title, null, 2024, 8.0, null, null,
                null, null, null, genre, null, null, null, alias, null);
    }
}
