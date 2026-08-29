package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogControllerTest {

    @Test
    void countsUseOneAggregateQueryAndFillMissingCategories() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("content_type", "movie", "content_count", 12L),
                Map.of("content_type", "short", "content_count", 3L)
        ));

        Result<Map<String, Long>> result = new CatalogController(jdbc).counts();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("movie", 12L)
                .containsEntry("short", 3L)
                .containsEntry("drama", 0L)
                .containsEntry("variety", 0L)
                .containsEntry("anime", 0L);
        verify(jdbc).queryForList(anyString());
    }
}
