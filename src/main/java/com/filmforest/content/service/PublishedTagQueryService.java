package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.content.dto.PageResult;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.model.ContentType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开标签查询。
 *
 * <p>标签表里的 usage_count 是历史维护字段，不能作为用户端筛选依据：历史数据可能来自
 * 草稿、下线内容或已经删除的关联。本服务始终从五类内容表的已发布记录实时聚合，避免把
 * 内部标签统计和公开目录混在一起。</p>
 */
@Service
public class PublishedTagQueryService {

    private static final int PUBLISHED_STATUS = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_HOT_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    public PublishedTagQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 返回有至少一条已上线内容的标签，usageCount 为公开内容数量。 */
    public IPage<Tag> pageTags(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        String publishedLinks = publishedLinksSql();

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT p.tag_id) FROM (" + publishedLinks + ") p "
                        + "INNER JOIN tag t ON t.id = p.tag_id AND t.is_deleted = 0",
                Long.class);
        String sql = """
                SELECT t.id, t.code, t.name, t.color, t.sort_order, t.is_system,
                       COUNT(DISTINCT CONCAT(p.content_type, ':', p.content_id)) AS usage_count
                FROM tag t
                INNER JOIN (%s) p ON p.tag_id = t.id
                WHERE t.is_deleted = 0
                GROUP BY t.id, t.code, t.name, t.color, t.sort_order, t.is_system
                ORDER BY usage_count DESC, t.sort_order ASC, t.id ASC
                LIMIT ? OFFSET ?
                """.formatted(publishedLinks);
        int offset = (safePage - 1) * safeSize;
        List<Tag> records = jdbcTemplate.query(sql, PublishedTagQueryService::mapTag, safeSize, offset);
        Page<Tag> result = new Page<>(safePage, safeSize, total);
        result.setRecords(records);
        return result;
    }

    public List<Tag> hotTags(int limit) {
        return pageTags(1, Math.min(MAX_HOT_LIMIT, Math.max(1, limit))).getRecords();
    }

    /**
     * 返回一条已上线内容的标签。标签的 usageCount 仍然采用公开聚合值，避免把内部历史数值
     * 泄露到公开响应中。
     */
    public List<Tag> getContentTags(String rawContentType, Long contentId) {
        ContentType contentType = ContentType.parse(rawContentType);
        String publishedLinks = publishedLinksSql();
        String table = contentType.code();
        String sql = """
                SELECT t.id, t.code, t.name, t.color, t.sort_order, t.is_system,
                       COUNT(DISTINCT CONCAT(p.content_type, ':', p.content_id)) AS usage_count
                FROM tag t
                INNER JOIN content_tag own
                        ON own.tag_id = t.id
                       AND own.content_type = ?
                       AND own.content_id = ?
                INNER JOIN %s content
                        ON content.id = own.content_id
                       AND content.status = ?
                       AND content.is_deleted = 0
                INNER JOIN (%s) p ON p.tag_id = t.id
                WHERE t.is_deleted = 0
                GROUP BY t.id, t.code, t.name, t.color, t.sort_order, t.is_system
                ORDER BY t.sort_order ASC, t.id ASC
                """.formatted(table, publishedLinks);
        return jdbcTemplate.query(sql, PublishedTagQueryService::mapTag,
                contentType.code(), contentId, PUBLISHED_STATUS);
    }

    /**
     * 根据标签返回公开内容 ID。没有指定类型时覆盖全部五类；返回的 contentType 始终为数据库
     * 规范值（例如 short_drama），不会把 short 别名泄露为另一种数据类型。
     */
    public PageResult<Map<String, Object>> getContentIdsByTag(Long tagId, String rawContentType,
                                                               int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        String publishedLinks = publishedLinksSql(rawContentType);
        String typePredicate = "";
        List<Object> args = new ArrayList<>();
        if (rawContentType != null && !rawContentType.isBlank()) {
            typePredicate = " AND p.content_type = ?";
            args.add(ContentType.parse(rawContentType).code());
        }

        String from = "FROM (" + publishedLinks + ") p "
                + "INNER JOIN tag t ON t.id = p.tag_id AND t.is_deleted = 0 "
                + "WHERE p.tag_id = ?" + typePredicate;
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(tagId);
        countArgs.addAll(args);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT DISTINCT p.content_id, p.content_type " + from + ") visible",
                Long.class, countArgs.toArray());

        String sql = "SELECT p.content_id, p.content_type "
                + from
                + " GROUP BY p.content_id, p.content_type "
                + " ORDER BY MAX(p.created_at) DESC, p.content_type ASC, p.content_id ASC "
                + " LIMIT ? OFFSET ?";
        List<Object> queryArgs = new ArrayList<>(countArgs);
        queryArgs.add(safeSize);
        queryArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> records = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("contentId", rs.getLong("content_id"));
            item.put("contentType", rs.getString("content_type"));
            return item;
        }, queryArgs.toArray());
        long safeTotal = total == null ? 0L : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + safeSize - 1) / safeSize;
        return new PageResult<>(records, safeTotal, safeSize, safePage, pages);
    }

    private static Tag mapTag(ResultSet rs, int rowNum) throws SQLException {
        Tag tag = new Tag();
        tag.setId(rs.getLong("id"));
        tag.setCode(rs.getString("code"));
        tag.setName(rs.getString("name"));
        tag.setColor(rs.getString("color"));
        tag.setSortOrder(rs.getInt("sort_order"));
        tag.setUsageCount(rs.getInt("usage_count"));
        tag.setSystem(rs.getInt("is_system"));
        return tag;
    }

    private static String publishedLinksSql() {
        return publishedLinksSql((String) null);
    }

    private static String publishedLinksSql(String rawContentType) {
        if (rawContentType != null && !rawContentType.isBlank()) {
            ContentType type = ContentType.parse(rawContentType);
            return publishedLinksSql(type);
        }
        return """
                SELECT DISTINCT ct.tag_id, ct.content_id, 'movie' AS content_type, m.created_at
                FROM content_tag ct INNER JOIN movie m ON m.id = ct.content_id
                WHERE ct.content_type = 'movie' AND m.status = 1 AND m.is_deleted = 0
                UNION ALL
                SELECT DISTINCT ct.tag_id, ct.content_id, 'drama' AS content_type, d.created_at
                FROM content_tag ct INNER JOIN drama d ON d.id = ct.content_id
                WHERE ct.content_type = 'drama' AND d.status = 1 AND d.is_deleted = 0
                UNION ALL
                SELECT DISTINCT ct.tag_id, ct.content_id, 'variety' AS content_type, v.created_at
                FROM content_tag ct INNER JOIN variety v ON v.id = ct.content_id
                WHERE ct.content_type = 'variety' AND v.status = 1 AND v.is_deleted = 0
                UNION ALL
                SELECT DISTINCT ct.tag_id, ct.content_id, 'anime' AS content_type, a.created_at
                FROM content_tag ct INNER JOIN anime a ON a.id = ct.content_id
                WHERE ct.content_type = 'anime' AND a.status = 1 AND a.is_deleted = 0
                UNION ALL
                SELECT DISTINCT ct.tag_id, ct.content_id, 'short_drama' AS content_type, s.created_at
                FROM content_tag ct INNER JOIN short_drama s ON s.id = ct.content_id
                WHERE ct.content_type = 'short_drama' AND s.status = 1 AND s.is_deleted = 0
                """;
    }

    private static String publishedLinksSql(ContentType type) {
        String table = type.code();
        return "SELECT DISTINCT ct.tag_id, ct.content_id, '" + table + "' AS content_type, c.created_at "
                + "FROM content_tag ct INNER JOIN " + table + " c ON c.id = ct.content_id "
                + "WHERE ct.content_type = '" + table + "' AND c.status = 1 AND c.is_deleted = 0";
    }
}
