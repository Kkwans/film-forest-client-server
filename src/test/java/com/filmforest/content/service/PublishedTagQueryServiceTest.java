package com.filmforest.content.service;

import com.filmforest.content.entity.Tag;
import com.filmforest.content.dto.PageResult;
import com.filmforest.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublishedTagQueryServiceTest {

    @Test
    void pageTagsAggregatesOnlyPublishedContentAcrossAllFiveTypes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        Tag tag = new Tag();
        tag.setId(7L);
        tag.setName("科幻");
        tag.setUsageCount(3);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(tag));

        var page = new PublishedTagQueryService(jdbcTemplate).pageTags(1, 20);

        assertThat(page.getTotal()).isEqualTo(2L);
        assertThat(page.getRecords()).containsExactly(tag);
        verify(jdbcTemplate).queryForObject(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("m.status = 1")
                        && sql.contains("d.status = 1")
                        && sql.contains("v.status = 1")
                        && sql.contains("a.status = 1")
                        && sql.contains("s.status = 1")
                        && sql.contains("is_deleted = 0")),
                eq(Long.class));
    }

    @Test
    void contentIdsNormalizeShortAliasAndOnlyReturnPublishedRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of(Map.of("contentId", 11L, "contentType", "short_drama")));

        PageResult<Map<String, Object>> result = new PublishedTagQueryService(jdbcTemplate)
                .getContentIdsByTag(4L, "short", 1, 20);

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).containsExactly(Map.of("contentId", 11L, "contentType", "short_drama"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(), any());
        assertThat(sql.getValue()).contains("short_drama", "c.status = 1", "c.is_deleted = 0");
    }

    @Test
    void unsupportedContentTypeIsRejectedBeforeBuildingDynamicSql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new PublishedTagQueryService(jdbcTemplate)
                                .getContentIdsByTag(1L, "language", 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的内容类型");
        verifyNoInteractions(jdbcTemplate);
    }
}
