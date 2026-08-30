package com.filmforest.content.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fixed-query profile aggregation. Published visibility and the aggregate cap
 * are enforced in SQL; the service never performs a per-item content lookup.
 */
@Repository
public class ProfileOverviewRepository {

    private static final String VISIBLE_LIST_ITEM_IDS = """
            SELECT i.id AS item_id, i.list_id
              FROM user_movie_list_item i
              JOIN movie c ON c.id = i.movie_id
                          AND c.status = 1
                          AND c.is_deleted = 0
             WHERE i.content_type = 'movie'
            UNION ALL
            SELECT i.id AS item_id, i.list_id
              FROM user_movie_list_item i
              JOIN drama c ON c.id = i.movie_id
                          AND c.status = 1
                          AND c.is_deleted = 0
             WHERE i.content_type = 'drama'
            UNION ALL
            SELECT i.id AS item_id, i.list_id
              FROM user_movie_list_item i
              JOIN variety c ON c.id = i.movie_id
                           AND c.status = 1
                           AND c.is_deleted = 0
             WHERE i.content_type = 'variety'
            UNION ALL
            SELECT i.id AS item_id, i.list_id
              FROM user_movie_list_item i
              JOIN anime c ON c.id = i.movie_id
                          AND c.status = 1
                          AND c.is_deleted = 0
             WHERE i.content_type = 'anime'
            UNION ALL
            SELECT i.id AS item_id, i.list_id
              FROM user_movie_list_item i
              JOIN short_drama c ON c.id = i.movie_id
                          AND c.status = 1
                          AND c.is_deleted = 0
             WHERE i.content_type = 'short_drama'
            """;

    private static final String VISIBLE_ITEMS = """
            SELECT i.id AS item_id,
                   l.id AS list_id,
                   l.name AS list_name,
                   l.type AS list_type,
                   i.movie_id,
                   i.content_type,
                   i.added_at,
                   i.watched_at,
                   i.rating AS user_rating,
                   i.note,
                   c.title,
                   c.poster_url AS cover,
                   c.year,
                   c.score_douban AS rating,
                   c.region,
                   c.genre,
                   c.duration,
                   NULL AS total_episode
              FROM user_movie_list_item i
              JOIN user_movie_list l ON l.id = i.list_id
              JOIN movie c ON c.id = i.movie_id
                          AND c.status = 1
                          AND c.is_deleted = 0
             WHERE l.user_id = :userId
               AND i.content_type = 'movie'
            UNION ALL
            SELECT i.id AS item_id,
                   l.id AS list_id,
                   l.name AS list_name,
                   l.type AS list_type,
                   i.movie_id,
                   i.content_type,
                   i.added_at,
                   i.watched_at,
                   i.rating AS user_rating,
                   i.note,
                   c.title,
                   c.poster_url AS cover,
                   c.year,
                   c.score_douban AS rating,
                   c.region,
                   c.genre,
                   c.duration,
                   c.total_episode
              FROM user_movie_list_item i
              JOIN user_movie_list l ON l.id = i.list_id
              JOIN drama c ON c.id = i.movie_id
                          AND c.status = 1
                          AND c.is_deleted = 0
             WHERE l.user_id = :userId
               AND i.content_type = 'drama'
            UNION ALL
            SELECT i.id AS item_id,
                   l.id AS list_id,
                   l.name AS list_name,
                   l.type AS list_type,
                   i.movie_id,
                   i.content_type,
                   i.added_at,
                   i.watched_at,
                   i.rating AS user_rating,
                   i.note,
                   c.title,
                   c.poster_url AS cover,
                   c.year,
                   c.score_douban AS rating,
                   c.region,
                   c.genre,
                   c.duration,
                   c.total_episode
              FROM user_movie_list_item i
              JOIN user_movie_list l ON l.id = i.list_id
              JOIN variety c ON c.id = i.movie_id
                           AND c.status = 1
                           AND c.is_deleted = 0
             WHERE l.user_id = :userId
               AND i.content_type = 'variety'
            UNION ALL
            SELECT i.id AS item_id,
                   l.id AS list_id,
                   l.name AS list_name,
                   l.type AS list_type,
                   i.movie_id,
                   i.content_type,
                   i.added_at,
                   i.watched_at,
                   i.rating AS user_rating,
                   i.note,
                   c.title,
                   c.poster_url AS cover,
                   c.year,
                   c.score_douban AS rating,
                   c.region,
                   c.genre,
                   c.duration,
                   c.total_episode
              FROM user_movie_list_item i
              JOIN user_movie_list l ON l.id = i.list_id
              JOIN anime c ON c.id = i.movie_id
                          AND c.status = 1
                          AND c.is_deleted = 0
             WHERE l.user_id = :userId
               AND i.content_type = 'anime'
            UNION ALL
            SELECT i.id AS item_id,
                   l.id AS list_id,
                   l.name AS list_name,
                   l.type AS list_type,
                   i.movie_id,
                   i.content_type,
                   i.added_at,
                   i.watched_at,
                   i.rating AS user_rating,
                   i.note,
                   c.title,
                   c.poster_url AS cover,
                   c.year,
                   c.score_douban AS rating,
                   c.region,
                   c.genre,
                   c.duration,
                   c.total_episode
              FROM user_movie_list_item i
              JOIN user_movie_list l ON l.id = i.list_id
              JOIN short_drama c ON c.id = i.movie_id
                          AND c.status = 1
                          AND c.is_deleted = 0
             WHERE l.user_id = :userId
               AND i.content_type = 'short_drama'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProfileOverviewRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StatsRow findStats(Long userId) {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM user_movie_list WHERE user_id = :userId) AS list_count,
                  (SELECT COUNT(*) FROM user_movie_list WHERE user_id = :userId AND is_default = 0) AS custom_count,
                  COALESCE(SUM(CASE WHEN l.type = 'want_to_watch' AND v.item_id IS NOT NULL THEN 1 ELSE 0 END), 0) AS want_count,
                  COALESCE(SUM(CASE WHEN l.type = 'watched' AND v.item_id IS NOT NULL THEN 1 ELSE 0 END), 0) AS watched_count
                FROM user_movie_list l
                LEFT JOIN (
                    %s
                ) v ON v.list_id = l.id
                WHERE l.user_id = :userId
                """.formatted(VISIBLE_LIST_ITEM_IDS);
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        return jdbc.queryForObject(sql, params, (rs, ignored) -> new StatsRow(
                rs.getLong("list_count"),
                rs.getLong("want_count"),
                rs.getLong("watched_count"),
                rs.getLong("custom_count")));
    }

    public List<ItemRow> findVisibleItems(Long userId, int limit) {
        String sql = """
                SELECT visible.*
                  FROM (
                    %s
                  ) visible
                 ORDER BY visible.added_at IS NULL, visible.added_at DESC, visible.item_id DESC
                 LIMIT :limit
                """.formatted(VISIBLE_ITEMS);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, ignored) -> new ItemRow(
                rs.getLong("item_id"),
                rs.getLong("list_id"),
                rs.getString("list_name"),
                rs.getString("list_type"),
                rs.getLong("movie_id"),
                rs.getString("content_type"),
                localDateTime(rs.getTimestamp("added_at")),
                localDateTime(rs.getTimestamp("watched_at")),
                rs.getString("title"),
                rs.getString("cover"),
                integer(rs.getObject("year")),
                rs.getBigDecimal("rating"),
                rs.getBigDecimal("user_rating"),
                rs.getString("note"),
                rs.getString("region"),
                rs.getString("genre"),
                integer(rs.getObject("duration")),
                integer(rs.getObject("total_episode"))));
    }

    private static LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    public record StatsRow(long listCount, long wantCount, long watchedCount, long customCount) {}

    public record ItemRow(
            long id,
            long listId,
            String listName,
            String listType,
            long movieId,
            String contentType,
            LocalDateTime addedAt,
            LocalDateTime watchedAt,
            String title,
            String cover,
            Integer year,
            BigDecimal rating,
            BigDecimal userRating,
            String note,
            String region,
            String genre,
            Integer duration,
            Integer totalEpisode) {}
}
