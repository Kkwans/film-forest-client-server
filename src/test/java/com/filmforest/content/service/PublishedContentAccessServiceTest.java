package com.filmforest.content.service;

import com.filmforest.content.model.ContentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublishedContentAccessServiceTest {

    @Test
    void checksPublishedStatusAgainstCanonicalContentTable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(42L), eq(ContentStatus.PUBLISHED.code()))).thenReturn(1);
        PublishedContentAccessService service = new PublishedContentAccessService(jdbcTemplate);

        assertThat(service.isPublished("short", 42L)).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Integer.class),
                eq(42L), eq(ContentStatus.PUBLISHED.code()));
        assertThat(sql.getValue()).contains("FROM `short_drama`").contains("status = ?");
    }

    @Test
    void rejectsMissingContentIdWithoutQueryingDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PublishedContentAccessService service = new PublishedContentAccessService(jdbcTemplate);

        assertThat(service.isPublished("movie", null)).isFalse();

        verifyNoInteractions(jdbcTemplate);
    }
}
