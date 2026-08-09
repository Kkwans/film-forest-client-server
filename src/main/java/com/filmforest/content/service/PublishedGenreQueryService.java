package com.filmforest.content.service;

import com.filmforest.content.dto.GenreOption;
import com.filmforest.content.model.ContentType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 查询指定内容类型当前真正可用的公开题材，避免展示只能命中草稿或下线内容的筛选项。 */
@Service
public class PublishedGenreQueryService {

    private final JdbcTemplate jdbcTemplate;

    public PublishedGenreQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GenreOption> listAvailable(String rawContentType) {
        ContentType contentType = ContentType.parse(rawContentType);
        String table = contentType.code();
        String sql = """
                SELECT t.id, t.code, t.name, t.color, COUNT(DISTINCT ct.content_id) AS content_count
                FROM tag t
                INNER JOIN tag_content_type tct
                        ON tct.tag_id = t.id AND tct.content_type = ?
                INNER JOIN content_tag ct
                        ON ct.tag_id = t.id AND ct.content_type = ?
                INNER JOIN %s content ON content.id = ct.content_id
                WHERE t.is_system = 1
                  AND t.is_deleted = 0
                  AND content.status = 1
                  AND content.is_deleted = 0
                GROUP BY t.id, t.code, t.name, t.color, t.sort_order
                HAVING COUNT(DISTINCT ct.content_id) > 0
                ORDER BY t.sort_order ASC, t.id ASC
                """.formatted(table);

        return jdbcTemplate.queryForList(sql, contentType.code(), contentType.code()).stream()
                .map(PublishedGenreQueryService::toOption)
                .toList();
    }

    private static GenreOption toOption(Map<String, Object> row) {
        return new GenreOption(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("code")),
                String.valueOf(row.get("name")),
                row.get("color") == null ? null : String.valueOf(row.get("color")),
                ((Number) row.get("content_count")).longValue());
    }
}
