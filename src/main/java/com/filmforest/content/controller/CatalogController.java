package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 首页/分类页使用的内容数量聚合接口。 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final JdbcTemplate jdbcTemplate;

    public CatalogController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/counts")
    public Result<Map<String, Long>> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
                SELECT 'movie' AS content_type, COUNT(*) AS content_count FROM movie WHERE status = 1 AND is_deleted = 0
                UNION ALL SELECT 'drama', COUNT(*) FROM drama WHERE status = 1 AND is_deleted = 0
                UNION ALL SELECT 'variety', COUNT(*) FROM variety WHERE status = 1 AND is_deleted = 0
                UNION ALL SELECT 'anime', COUNT(*) FROM anime WHERE status = 1 AND is_deleted = 0
                UNION ALL SELECT 'short', COUNT(*) FROM short_drama WHERE status = 1 AND is_deleted = 0
                """).forEach(row -> {
            String type = String.valueOf(row.get("content_type"));
            Object rawCount = row.get("content_count");
            counts.put(type, rawCount instanceof Number number ? number.longValue() : 0L);
        });
        counts.putIfAbsent("movie", 0L);
        counts.putIfAbsent("drama", 0L);
        counts.putIfAbsent("variety", 0L);
        counts.putIfAbsent("anime", 0L);
        counts.putIfAbsent("short", 0L);
        return Result.ok(counts);
    }
}
