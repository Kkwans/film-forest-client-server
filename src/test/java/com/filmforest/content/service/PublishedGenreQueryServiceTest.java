package com.filmforest.content.service;

import com.filmforest.content.dto.GenreOption;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishedGenreQueryServiceTest {

    @Test
    void onlyReturnsGenresBackedByPublishedContentOfRequestedType() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq("movie"), eq("movie")))
                .thenReturn(List.of(Map.of(
                        "id", 2L,
                        "code", "comedy",
                        "name", "喜剧",
                        "content_count", 1L)));

        List<GenreOption> result = new PublishedGenreQueryService(jdbcTemplate)
                .listAvailable("movie");

        assertThat(result).containsExactly(new GenreOption(2L, "comedy", "喜剧", null, 1L));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq("movie"), eq("movie"));
        assertThat(sql.getValue())
                .contains("INNER JOIN movie content")
                .contains("content.status = 1")
                .contains("content.is_deleted = 0");
    }
}
