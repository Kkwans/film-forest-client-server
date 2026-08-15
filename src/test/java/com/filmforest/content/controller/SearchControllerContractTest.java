package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.PageResult;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.AnimeService;
import com.filmforest.content.service.ContentResourceFilter;
import com.filmforest.content.service.DramaService;
import com.filmforest.content.service.MovieService;
import com.filmforest.content.service.ShortDramaService;
import com.filmforest.content.service.VarietyService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void matchedFieldsExposeAliasWriterPeopleGenreAndYearMatches() {
        assertThat(SearchController.matchedFields("别名", "标题", "[\"别名\"]", null,
                null, null, null, 2024)).containsExactly("alias");
        assertThat(SearchController.matchedFields("编剧", "标题", null, "[\"编剧\"]",
                null, null, null, 2024)).containsExactly("writer");
        assertThat(SearchController.matchedFields("导演", "标题", null, null,
                "[\"导演\"]", null, null, 2024)).containsExactly("director");
        assertThat(SearchController.matchedFields("演员", "标题", null, null,
                null, "[\"演员\"]", null, 2024)).containsExactly("actor");
        assertThat(SearchController.matchedFields("2024", "标题", null, null,
                null, null, "[\"剧情\"]", 2024)).containsExactly("year");
    }

    @Test
    void searchUsesWriterContractAndAppliesRegionGenreAndLanguageFilters() {
        initialize(Movie.class, "search-contract-movie");
        MovieService movieService = mock(MovieService.class);
        Movie movie = new Movie();
        movie.setId(7L);
        movie.setTitle("测试电影");
        movie.setAlias("[\"别名\"]");
        movie.setWriter("[\"编剧甲\"]");
        movie.setDirector("[\"导演甲\"]");
        movie.setActor("[\"主演甲\"]");
        movie.setGenre("[\"剧情\"]");
        movie.setRegion("[\"美国\"]");
        movie.setLanguage("[\"英语\"]");
        movie.setReleaseDate("2024-01-02");
        movie.setScoreDoubanCount(100);
        movie.setScoreImdbCount(null);
        movie.setScoreRtCriticCount(null);
        movie.setScoreRtAudienceCount(25);
        when(movieService.list(any(Wrapper.class))).thenReturn(List.of(movie));

        SearchController controller = controller(movieService);
        Result<?> response = controller.search("编剧甲", 1, 20, "movie", null, null,
                " 美国 ", " 剧情 ", " 英语 ", null, "relevance", "desc");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Movie>> wrapper = (ArgumentCaptor<Wrapper<Movie>>) (ArgumentCaptor<?>)
                ArgumentCaptor.forClass(Wrapper.class);
        verify(movieService).list(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment())
                .contains("writer", "director", "actor", "alias", "region", "genre", "language");
        assertThat(((AbstractWrapper<?, ?, ?>) wrapper.getValue()).getParamNameValuePairs().values())
                .contains("%美国%", "%剧情%", "%英语%");

        PageResult<?> page = (PageResult<?>) response.getData();
        SearchController.SearchResult result = (SearchController.SearchResult) page.records().get(0);
        assertThat(result.alias()).isEqualTo("[\"别名\"]");
        assertThat(result.writer()).isEqualTo("[\"编剧甲\"]");
        assertThat(result.releaseDate()).isEqualTo("2024-01-02");
        assertThat(result.totalEpisode()).isNull();
        assertThat(result.scoreDoubanCount()).isEqualTo(100);
        assertThat(result.scoreImdbCount()).isNull();
        assertThat(result.scoreRtCriticCount()).isNull();
        assertThat(result.scoreRtAudienceCount()).isEqualTo(25);
    }

    @Test
    void suggestionsSearchDirectorAndActorInAdditionToAliasAndWriter() {
        initialize(Movie.class, "suggest-contract-movie");
        MovieService movieService = mock(MovieService.class);
        Movie movie = new Movie();
        movie.setTitle("导演主演电影");
        Page<Movie> page = new Page<>(1, 10);
        page.setRecords(List.of(movie));
        when(movieService.page(any(Page.class), any(Wrapper.class))).thenReturn(page);

        SearchController controller = controller(movieService);
        Result<?> response = controller.suggest("导演");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Movie>> wrapper = (ArgumentCaptor<Wrapper<Movie>>) (ArgumentCaptor<?>)
                ArgumentCaptor.forClass(Wrapper.class);
        verify(movieService).page(any(Page.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment())
                .contains("alias", "writer", "director", "actor");
        assertThat((List<?>) response.getData()).singleElement().isEqualTo("导演主演电影");
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

    private SearchController controller(MovieService movieService) {
        DramaService dramaService = mock(DramaService.class);
        VarietyService varietyService = mock(VarietyService.class);
        AnimeService animeService = mock(AnimeService.class);
        ShortDramaService shortDramaService = mock(ShortDramaService.class);
        when(dramaService.page(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>());
        when(varietyService.page(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>());
        when(animeService.page(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>());
        when(shortDramaService.page(any(Page.class), any(Wrapper.class))).thenReturn(new Page<>());
        return new SearchController(movieService, dramaService, varietyService, animeService, shortDramaService,
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(ContentResourceFilter.class));
    }

    private void initialize(Class<?> entityType, String namespace) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), namespace);
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
