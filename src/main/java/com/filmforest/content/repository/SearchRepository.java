package com.filmforest.content.repository;

import com.filmforest.content.controller.SearchController;
import com.filmforest.content.model.ContentType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 搜索专用 SQL 投影。相关度、状态筛选、分页和 total 均在数据库完成，
 * 避免把五张内容表的全部实体加载到 JVM 后再排序/过滤。
 */
@Repository
public class SearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SearchPage search(String keyword,
                             Set<ContentType> selectedTypes,
                             Long tagId,
                             Integer year,
                             String region,
                             String genre,
                             String language,
                             Boolean hasResource,
                             String userStatus,
                             Long userId,
                             String sort,
                             boolean desc,
                             int page,
                             int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", keyword)
                .addValue("keywordLower", keyword.toLowerCase(Locale.ROOT))
                .addValue("keywordYear", SearchController.parseKeywordYear(keyword))
                .addValue("year", year)
                .addValue("region", region)
                .addValue("genre", genre)
                .addValue("language", language)
                .addValue("tagId", tagId)
                .addValue("userId", userId)
                .addValue("hasResource", hasResource)
                .addValue("userStatus", userStatus);

        Set<ContentType> types = selectedTypes == null || selectedTypes.isEmpty()
                ? EnumSet.allOf(ContentType.class)
                : EnumSet.copyOf(selectedTypes);
        List<String> selects = new ArrayList<>();
        for (ContentType type : types) {
            selects.add(selectFor(type, keyword, tagId, year, region, genre, language, hasResource, userStatus));
        }
        if (selects.isEmpty()) return new SearchPage(List.of(), 0);
        String union = String.join(" UNION ALL ", selects);
        String from = " FROM (" + union + ") content";
        String order = orderBy(sort, desc);
        long offset = (long) (Math.max(page, 1) - 1) * size;
        params.addValue("limit", size).addValue("offset", offset);

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, params, Long.class);
        List<SearchController.SearchResult> records = jdbc.query(
                "SELECT content.*" + from + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                params,
                (rs, rowNum) -> new SearchController.SearchResult(
                        rs.getLong("id"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("cover"),
                        integer(rs.getObject("year")),
                        decimal(rs.getBigDecimal("rating")),
                        decimal(rs.getBigDecimal("rating_imdb")),
                        decimal(rs.getBigDecimal("rating_rt")),
                        rs.getString("summary"),
                        rs.getString("director"),
                        rs.getString("actor"),
                        rs.getString("genre"),
                        rs.getString("region"),
                        integer(rs.getObject("duration")),
                        integer(rs.getObject("total_episode")),
                        rs.getString("alias"),
                        timestampMs(rs.getTimestamp("updated_at")),
                        rs.getString("writer"),
                        rs.getString("director"),
                        rs.getString("actor"),
                        rs.getString("release_date"),
                        matchedFields(keyword, rs.getString("title"), rs.getString("alias"),
                                rs.getString("writer"), rs.getString("director"), rs.getString("actor"),
                                rs.getString("genre"), integer(rs.getObject("year"))),
                        integer(rs.getObject("score_douban_count")),
                        integer(rs.getObject("score_imdb_count")),
                        integer(rs.getObject("score_rt_critic_count")),
                        integer(rs.getObject("score_rt_audience_count"))))
                .stream().toList();
        return new SearchPage(records, total == null ? 0 : total);
    }

    private String selectFor(ContentType type, String keyword, Long tagId, Integer year,
                             String region, String genre, String language, Boolean hasResource,
                             String userStatus) {
        String table = switch (type) {
            case MOVIE -> "movie";
            case DRAMA -> "drama";
            case VARIETY -> "variety";
            case ANIME -> "anime";
            case SHORT_DRAMA -> "short_drama";
        };
        String code = type.code();
        String keywordCondition = SearchController.matchesTypeKeyword(type, keyword)
                ? "1=1"
                : "(c.title LIKE CONCAT('%', :keyword, '%')"
                + " OR c.alias LIKE CONCAT('%', :keyword, '%')"
                + " OR c.writer LIKE CONCAT('%', :keyword, '%')"
                + " OR c.director LIKE CONCAT('%', :keyword, '%')"
                + " OR c.actor LIKE CONCAT('%', :keyword, '%')"
                + " OR c.genre LIKE CONCAT('%', :keyword, '%')"
                + (SearchController.parseKeywordYear(keyword) == null ? "" : " OR c.year = :keywordYear")
                + ")";
        String tagCondition = tagId == null ? "" :
                " AND EXISTS (SELECT 1 FROM content_tag ct JOIN tag t ON t.id = ct.tag_id"
                        + " AND t.is_system = 1 AND t.is_deleted = 0"
                        + " WHERE ct.tag_id = :tagId AND ct.content_type = '" + code + "' AND ct.content_id = c.id)";
        String resourceCondition = hasResource == null ? "" :
                (hasResource ? " AND (" : " AND NOT (")
                        + resourceExists("resource_online", code)
                        + " OR " + resourceExists("resource_magnet", code)
                        + " OR " + resourceExists("resource_cloud", code) + ")";
        String statusCondition = userStatus == null || "all".equals(userStatus) ? "" :
                " AND " + userStatusPredicate(userStatus, code);
        String relevance = "CASE WHEN LOWER(c.title) = :keywordLower THEN 1000"
                + " WHEN LOWER(c.title) LIKE CONCAT(:keywordLower, '%') THEN 850"
                + " WHEN c.title LIKE CONCAT('%', :keyword, '%') THEN 700"
                + " WHEN c.alias LIKE CONCAT('%', :keyword, '%') THEN 600"
                + " WHEN c.genre LIKE CONCAT('%', :keyword, '%') THEN 500"
                + " WHEN c.writer LIKE CONCAT('%', :keyword, '%')"
                + " OR c.actor LIKE CONCAT('%', :keyword, '%')"
                + " OR c.director LIKE CONCAT('%', :keyword, '%') THEN 400"
                + " WHEN c.year = :keywordYear THEN 300 ELSE 0 END AS relevance_score";
        String episode = type == ContentType.MOVIE ? "NULL" : "c.total_episode";
        String rt = type == ContentType.MOVIE ? "c.score_rt" : "NULL";
        String rtCritic = type == ContentType.MOVIE ? "c.score_rt_critic_count" : "NULL";
        String rtAudience = type == ContentType.MOVIE ? "c.score_rt_audience_count" : "NULL";
        return "SELECT c.id, '" + code + "' AS type, c.title, c.poster_url AS cover, c.year,"
                + " c.director AS director, c.actor, c.genre, c.region, c.language,"
                + " c.duration, " + episode + " AS total_episode, c.alias, c.updated_at, c.writer, c.release_date,"
                + " c.storyline AS summary, c.score_douban AS rating, c.score_imdb AS rating_imdb, " + rt + " AS rating_rt,"
                + " c.score_douban_count, c.score_imdb_count, " + rtCritic + " AS score_rt_critic_count,"
                + " " + rtAudience + " AS score_rt_audience_count, " + relevance
                + " FROM " + table + " c"
                + " WHERE c.status = 1 AND c.is_deleted = 0"
                + " AND " + keywordCondition
                + " AND (:year IS NULL OR c.year = :year)"
                + " AND (:region IS NULL OR c.region LIKE CONCAT('%', :region, '%'))"
                + " AND (:genre IS NULL OR c.genre LIKE CONCAT('%', :genre, '%'))"
                + " AND (:language IS NULL OR c.language LIKE CONCAT('%', :language, '%'))"
                + tagCondition + resourceCondition + statusCondition;
    }

    private String resourceExists(String table, String contentType) {
        return "EXISTS (SELECT 1 FROM " + table + " r WHERE r.content_type = '" + contentType
                + "' AND r.content_id = c.id AND r.is_deleted = 0 AND r.enabled = 1 AND r.removed_at IS NULL)";
    }

    private String userStatusPredicate(String status, String contentType) {
        String exists = "EXISTS (SELECT 1 FROM user_movie_list_item li JOIN user_movie_list ul"
                + " ON ul.id = li.list_id WHERE ul.user_id = :userId AND li.movie_id = c.id"
                + " AND li.content_type = '" + contentType + "')";
        String watched = "EXISTS (SELECT 1 FROM user_movie_list_item li JOIN user_movie_list ul"
                + " ON ul.id = li.list_id WHERE ul.user_id = :userId AND li.movie_id = c.id"
                + " AND li.content_type = '" + contentType + "' AND ul.type = 'watched')";
        return switch (status) {
            case "watched" -> watched;
            case "unwatched" -> "NOT (" + watched + ")";
            case "listed" -> exists;
            case "unlisted" -> "NOT (" + exists + ")";
            default -> "1=1";
        };
    }

    private String orderBy(String sort, boolean desc) {
        String direction = desc ? "DESC" : "ASC";
        String field = switch (sort) {
            case "year" -> "content.year";
            case "imdb" -> "content.rating_imdb";
            case "rt" -> "content.rating_rt";
            case "rating", "douban" -> "content.rating";
            case "latest" -> "content.updated_at";
            default -> "content.relevance_score";
        };
        return field + " " + direction + ", content.type ASC, content.id ASC";
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Long timestampMs(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static List<String> matchedFields(String keyword, String title, String alias, String writer,
                                               String director, String actor, String genre, Integer year) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return List.of();
        List<String> fields = new ArrayList<>();
        if (contains(title, normalized)) fields.add("title");
        if (contains(alias, normalized)) fields.add("alias");
        if (contains(writer, normalized)) fields.add("writer");
        if (contains(director, normalized)) fields.add("director");
        if (contains(actor, normalized)) fields.add("actor");
        if (contains(genre, normalized)) fields.add("genre");
        if (year != null && String.valueOf(year).equals(normalized)) fields.add("year");
        return List.copyOf(fields);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    public record SearchPage(List<SearchController.SearchResult> records, long total) {
    }
}
