package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.PageResult;
import com.filmforest.content.entity.*;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.model.ContentStatus;
import com.filmforest.content.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
/**
 * 全局搜索接口
 * 支持电影/剧集/综艺/动漫/短剧的跨类型搜索，统一排序和分页
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final MovieService movieService;
    private final DramaService dramaService;
    private final VarietyService varietyService;
    private final AnimeService animeService;
    private final ShortDramaService shortDramaService;
    private final JdbcTemplate jdbcTemplate;

    public SearchController(MovieService movieService,
                            DramaService dramaService,
                            VarietyService varietyService,
                            AnimeService animeService,
                            ShortDramaService shortDramaService,
                            JdbcTemplate jdbcTemplate) {
        this.movieService = movieService;
        this.dramaService = dramaService;
        this.varietyService = varietyService;
        this.animeService = animeService;
        this.shortDramaService = shortDramaService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 搜索建议：标题前缀匹配，返回 Top 10
     * 输入框每 300ms 触发一次，快速返回标题建议
     */
    @GetMapping("/suggest")
    public Result<?> suggest(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        String kw = q.trim();
        log.debug("[Search] suggest q={}", kw);

        int perTableLimit = 10;
        Set<String> seen = new LinkedHashSet<>();

        // 从 5 张表中分别查询标题匹配
        suggestFromTable(movieService, Movie::getTitle, Movie::getAlias, Movie::getStatus,
                kw, perTableLimit, seen);
        suggestFromTable(dramaService, Drama::getTitle, Drama::getAlias, Drama::getStatus,
                kw, perTableLimit, seen);
        suggestFromTable(varietyService, Variety::getTitle, Variety::getAlias, Variety::getStatus,
                kw, perTableLimit, seen);
        suggestFromTable(animeService, Anime::getTitle, Anime::getAlias, Anime::getStatus,
                kw, perTableLimit, seen);
        suggestFromTable(shortDramaService, ShortDrama::getTitle, ShortDrama::getAlias, ShortDrama::getStatus,
                kw, perTableLimit, seen);

        // 取前 10 个
        List<String> suggestions = seen.stream().limit(10).collect(Collectors.toList());
        return Result.ok(suggestions);
    }

    /** 热门搜索：基于真实搜索日志聚合近 30 天关键词。 */
    @GetMapping("/hot")
    public Result<?> hotSearch() {
        log.debug("[Search] hot search");
        try {
            return Result.ok(jdbcTemplate.queryForList("""
                    SELECT keyword AS title, COUNT(*) AS searchCount, MAX(created_at) AS lastSearchedAt
                    FROM search_log
                    WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                      AND keyword IS NOT NULL AND keyword <> ''
                    GROUP BY keyword
                    ORDER BY searchCount DESC, lastSearchedAt DESC
                    LIMIT 10
                    """));
        } catch (Exception e) {
            log.warn("[Search] 热门词聚合失败", e);
            return Result.ok(Collections.emptyList());
        }
    }

    // ==================== suggest 辅助方法 ====================

    /** 从单表查询标题模糊匹配 */
    private <T> void suggestFromTable(
            com.baomidou.mybatisplus.spring.service.IService<T> service,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> titleField,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> aliasField,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> statusField,
            String keyword, int limit, Set<String> seen) {
        try {
            Page<T> p = service.page(new Page<>(1, limit),
                    new LambdaQueryWrapper<T>()
                            .eq(statusField, ContentStatus.PUBLISHED.code())
                            .and(w -> w.like(titleField, keyword)
                                    .or()
                                    .like(aliasField, keyword)));
            for (T entity : p.getRecords()) {
                // 通过反射获取 title
                String title = getTitleFromEntity(entity);
                if (title != null && !title.isBlank()) {
                    seen.add(title);
                }
            }
        } catch (Exception e) {
            log.error("[Search] suggest 查询异常", e);
        }
    }

    /** 从实体中提取 title 字段 */
    private String getTitleFromEntity(Object entity) {
        try {
            var method = entity.getClass().getMethod("getTitle");
            Object val = method.invoke(entity);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 全局搜索（合并电影/剧集/综艺/动漫/短剧）
     * 使用堆排序避免全量排序，只维护 top-N 结果
     */
    @GetMapping
    public Result<?> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String typeFilter,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "desc") String sortDir) {

        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("[Search] 关键词为空");
            return Result.fail("关键词不能为空");
        }
        try {
            validateTagId(tagId);
        } catch (IllegalArgumentException invalid) {
            return Result.fail(400, invalid.getMessage());
        }

        String kw = keyword.trim();
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long from = (safePage - 1L) * safeSize;
        long perTableLimit = from + safeSize;
        Set<ContentType> selectedTypes = parseTypeFilter(typeFilter);
        Map<ContentType, Set<Long>> tagMatches = loadTagMatches(tagId, selectedTypes);
        boolean desc = "desc".equalsIgnoreCase(sortDir);
        log.debug("[Search] keyword={}, page={}, size={}, types={}, tagId={}, sort={}, sortDir={}",
                kw, safePage, safeSize, selectedTypes, tagId, sort, sortDir);

        List<SearchResult> allResults = new ArrayList<>();

        long total = 0;
        if (shouldSearch(ContentType.MOVIE, selectedTypes, tagId, tagMatches)) {
            total += searchMovies(kw, perTableLimit, sort, desc, tagMatches.get(ContentType.MOVIE), allResults);
        }
        if (shouldSearch(ContentType.DRAMA, selectedTypes, tagId, tagMatches)) {
            total += searchDramas(kw, perTableLimit, sort, desc, tagMatches.get(ContentType.DRAMA), allResults);
        }
        if (shouldSearch(ContentType.VARIETY, selectedTypes, tagId, tagMatches)) {
            total += searchVarieties(kw, perTableLimit, sort, desc, tagMatches.get(ContentType.VARIETY), allResults);
        }
        if (shouldSearch(ContentType.ANIME, selectedTypes, tagId, tagMatches)) {
            total += searchAnimes(kw, perTableLimit, sort, desc, tagMatches.get(ContentType.ANIME), allResults);
        }
        if (shouldSearch(ContentType.SHORT_DRAMA, selectedTypes, tagId, tagMatches)) {
            total += searchShortDramas(kw, perTableLimit, sort, desc, tagMatches.get(ContentType.SHORT_DRAMA), allResults);
        }

        Comparator<SearchResult> comparator = getSearchResultComparator(sort, desc);
        List<SearchResult> pageData = allResults.stream()
                .sorted(comparator.thenComparing(SearchResult::type).thenComparing(SearchResult::id))
                .skip(from)
                .limit(safeSize)
                .collect(Collectors.toList());

        // 记录搜索日志
        try {
            jdbcTemplate.update(
                "INSERT INTO search_log (keyword, result_count, source, created_at) VALUES (?, ?, 'web', NOW())",
                kw, total);
        } catch (Exception e) {
            log.warn("[Search] 记录搜索日志失败: {}", kw, e);
        }

        long pages = total == 0 ? 0 : (total + safeSize - 1) / safeSize;
        return Result.ok(new PageResult<>(pageData, total, safeSize, safePage, pages));
    }

    // ==================== 各类型搜索方法 ====================

    private long searchMovies(String kw, long limit, String sort, boolean desc,
                              Set<Long> taggedContentIds, List<SearchResult> results) {
        try {
            LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<Movie>()
                    .eq(Movie::getStatus, ContentStatus.PUBLISHED.code())
                    .and(w -> w.like(Movie::getTitle, kw)
                            .or().like(Movie::getAlias, kw)
                            .or().like(Movie::getActor, kw)
                            .or().like(Movie::getDirector, kw));
            wrapper.in(taggedContentIds != null, Movie::getId, taggedContentIds);
            applyMovieSort(wrapper, sort, !desc);
            Page<Movie> p = movieService.page(new Page<>(1, limit), wrapper);
            for (Movie m : p.getRecords()) {
                results.add(new SearchResult(
                        m.getId(), "movie", m.getTitle(),
                        m.getPosterUrl(), m.getYear(),
                        toDouble(m.getScoreDouban()), toDouble(m.getScoreImdb()), toDouble(m.getScoreRt()),
                        m.getStoryline(), m.getDirector(), m.getActor(),
                        m.getGenre(), m.getRegion(), m.getDuration(), null, m.getAlias(),
                        toTimestamp(m.getUpdatedAt())));
            }
            return p.getTotal();
        } catch (Exception e) {
            log.error("[Search] 电影搜索异常: keyword={}", kw, e);
            throw new IllegalStateException("电影搜索失败", e);
        }
    }

    private long searchDramas(String kw, long limit, String sort, boolean desc,
                              Set<Long> taggedContentIds, List<SearchResult> results) {
        try {
            LambdaQueryWrapper<Drama> wrapper = new LambdaQueryWrapper<Drama>()
                    .eq(Drama::getStatus, ContentStatus.PUBLISHED.code())
                    .and(w -> w.like(Drama::getTitle, kw)
                            .or().like(Drama::getAlias, kw)
                            .or().like(Drama::getActor, kw));
            wrapper.in(taggedContentIds != null, Drama::getId, taggedContentIds);
            applyDramaSort(wrapper, sort, !desc);
            Page<Drama> p = dramaService.page(new Page<>(1, limit), wrapper);
            for (Drama d : p.getRecords()) {
                results.add(new SearchResult(
                        d.getId(), "drama", d.getTitle(),
                        d.getPosterUrl(), d.getYear(),
                        toDouble(d.getScoreDouban()), toDouble(d.getScoreImdb()), null,
                        d.getStoryline(), d.getDirector(), d.getActor(),
                        d.getGenre(), d.getRegion(), null, d.getTotalEpisode(), d.getAlias(),
                        toTimestamp(d.getUpdatedAt())));
            }
            return p.getTotal();
        } catch (Exception e) {
            log.error("[Search] 剧集搜索异常: keyword={}", kw, e);
            throw new IllegalStateException("剧集搜索失败", e);
        }
    }

    private long searchVarieties(String kw, long limit, String sort, boolean desc,
                                 Set<Long> taggedContentIds, List<SearchResult> results) {
        try {
            LambdaQueryWrapper<Variety> wrapper = new LambdaQueryWrapper<Variety>()
                    .eq(Variety::getStatus, ContentStatus.PUBLISHED.code())
                    .and(w -> w.like(Variety::getTitle, kw).or().like(Variety::getAlias, kw));
            wrapper.in(taggedContentIds != null, Variety::getId, taggedContentIds);
            applyVarietySort(wrapper, sort, !desc);
            Page<Variety> p = varietyService.page(new Page<>(1, limit), wrapper);
            for (Variety v : p.getRecords()) {
                results.add(new SearchResult(
                        v.getId(), "variety", v.getTitle(),
                        v.getPosterUrl(), v.getYear(),
                        toDouble(v.getScoreDouban()), null, null,
                        v.getStoryline(), v.getDirector(), v.getActor(),
                        v.getGenre(), v.getRegion(), null, v.getTotalEpisode(), v.getAlias(),
                        toTimestamp(v.getUpdatedAt())));
            }
            return p.getTotal();
        } catch (Exception e) {
            log.error("[Search] 综艺搜索异常: keyword={}", kw, e);
            throw new IllegalStateException("综艺搜索失败", e);
        }
    }

    private long searchAnimes(String kw, long limit, String sort, boolean desc,
                              Set<Long> taggedContentIds, List<SearchResult> results) {
        try {
            LambdaQueryWrapper<Anime> wrapper = new LambdaQueryWrapper<Anime>()
                    .eq(Anime::getStatus, ContentStatus.PUBLISHED.code())
                    .and(w -> w.like(Anime::getTitle, kw)
                            .or().like(Anime::getAlias, kw)
                            .or().like(Anime::getActor, kw));
            wrapper.in(taggedContentIds != null, Anime::getId, taggedContentIds);
            applyAnimeSort(wrapper, sort, !desc);
            Page<Anime> p = animeService.page(new Page<>(1, limit), wrapper);
            for (Anime a : p.getRecords()) {
                results.add(new SearchResult(
                        a.getId(), "anime", a.getTitle(),
                        a.getPosterUrl(), a.getYear(),
                        toDouble(a.getScoreDouban()), null, null,
                        a.getStoryline(), a.getDirector(), a.getActor(),
                        a.getGenre(), a.getRegion(), null, a.getTotalEpisode(), a.getAlias(),
                        toTimestamp(a.getUpdatedAt())));
            }
            return p.getTotal();
        } catch (Exception e) {
            log.error("[Search] 动漫搜索异常: keyword={}", kw, e);
            throw new IllegalStateException("动漫搜索失败", e);
        }
    }

    private long searchShortDramas(String kw, long limit, String sort, boolean desc,
                                   Set<Long> taggedContentIds, List<SearchResult> results) {
        try {
            LambdaQueryWrapper<ShortDrama> wrapper = new LambdaQueryWrapper<ShortDrama>()
                    .eq(ShortDrama::getStatus, ContentStatus.PUBLISHED.code())
                    .and(w -> w.like(ShortDrama::getTitle, kw).or().like(ShortDrama::getAlias, kw));
            wrapper.in(taggedContentIds != null, ShortDrama::getId, taggedContentIds);
            applyShortDramaSort(wrapper, sort, !desc);
            Page<ShortDrama> p = shortDramaService.page(new Page<>(1, limit), wrapper);
            for (ShortDrama s : p.getRecords()) {
                results.add(new SearchResult(
                        s.getId(), "short_drama", s.getTitle(),
                        s.getPosterUrl(), s.getYear(),
                        null, null, null,
                        s.getStoryline(), null, null,
                        s.getGenre(), s.getRegion(), null, s.getTotalEpisode(), s.getAlias(),
                        toTimestamp(s.getUpdatedAt())));
            }
            return p.getTotal();
        } catch (Exception e) {
            log.error("[Search] 短剧搜索异常: keyword={}", kw, e);
            throw new IllegalStateException("短剧搜索失败", e);
        }
    }

    // ==================== 工具方法 ====================

    static Set<ContentType> parseTypeFilter(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return EnumSet.allOf(ContentType.class);
        }
        EnumSet<ContentType> selected = EnumSet.noneOf(ContentType.class);
        Arrays.stream(raw.split(","))
                .filter(value -> !value.isBlank())
                .map(ContentType::parse)
                .forEach(selected::add);
        return selected.isEmpty() ? EnumSet.allOf(ContentType.class) : selected;
    }

    static void validateTagId(Long tagId) {
        if (tagId != null && tagId <= 0) {
            throw new IllegalArgumentException("tagId 必须为正整数");
        }
    }

    private Map<ContentType, Set<Long>> loadTagMatches(Long tagId, Set<ContentType> selectedTypes) {
        if (tagId == null) return Map.of();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ct.content_type AS contentType, ct.content_id AS contentId
                FROM content_tag ct
                JOIN tag t ON t.id = ct.tag_id AND t.is_system = 1 AND t.is_deleted = 0
                JOIN tag_content_type tct
                  ON tct.tag_id = ct.tag_id AND tct.content_type = ct.content_type
                WHERE ct.tag_id = ?
                """, tagId);
        return groupTagMatches(rows, selectedTypes);
    }

    static Map<ContentType, Set<Long>> groupTagMatches(List<Map<String, Object>> rows,
                                                        Set<ContentType> selectedTypes) {
        EnumMap<ContentType, Set<Long>> grouped = new EnumMap<>(ContentType.class);
        if (rows == null || rows.isEmpty()) return grouped;
        for (Map<String, Object> row : rows) {
            Object rawType = valueIgnoreCase(row, "contentType");
            Object rawId = valueIgnoreCase(row, "contentId");
            if (rawType == null || !(rawId instanceof Number number)) continue;
            try {
                ContentType type = ContentType.parse(rawType.toString());
                long contentId = number.longValue();
                if (contentId > 0 && selectedTypes.contains(type)) {
                    grouped.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(contentId);
                }
            } catch (RuntimeException ignored) {
                // Historical invalid content_type rows are not valid public search matches.
            }
        }
        return grouped;
    }

    private static Object valueIgnoreCase(Map<String, Object> row, String key) {
        if (row == null) return null;
        if (row.containsKey(key)) return row.get(key);
        return row.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean shouldSearch(ContentType type, Set<ContentType> selectedTypes, Long tagId,
                                 Map<ContentType, Set<Long>> tagMatches) {
        if (!selectedTypes.contains(type)) return false;
        return tagId == null || !tagMatches.getOrDefault(type, Set.of()).isEmpty();
    }

    private void applyMovieSort(LambdaQueryWrapper<Movie> wrapper, String sort, boolean asc) {
        switch (sort) {
            case "year" -> wrapper.orderBy(true, asc, Movie::getYear);
            case "douban" -> wrapper.orderBy(true, asc, Movie::getScoreDouban);
            case "imdb" -> wrapper.orderBy(true, asc, Movie::getScoreImdb);
            case "rt" -> wrapper.orderBy(true, asc, Movie::getScoreRt);
            default -> wrapper.orderBy(true, asc, Movie::getUpdatedAt);
        }
    }

    private void applyDramaSort(LambdaQueryWrapper<Drama> wrapper, String sort, boolean asc) {
        switch (sort) {
            case "year" -> wrapper.orderBy(true, asc, Drama::getYear);
            case "douban" -> wrapper.orderBy(true, asc, Drama::getScoreDouban);
            case "imdb" -> wrapper.orderBy(true, asc, Drama::getScoreImdb);
            case "rt" -> wrapper.orderBy(true, asc, Drama::getId);
            default -> wrapper.orderBy(true, asc, Drama::getUpdatedAt);
        }
    }

    private void applyVarietySort(LambdaQueryWrapper<Variety> wrapper, String sort, boolean asc) {
        switch (sort) {
            case "year" -> wrapper.orderBy(true, asc, Variety::getYear);
            case "douban" -> wrapper.orderBy(true, asc, Variety::getScoreDouban);
            case "imdb", "rt" -> wrapper.orderBy(true, asc, Variety::getId);
            default -> wrapper.orderBy(true, asc, Variety::getUpdatedAt);
        }
    }

    private void applyAnimeSort(LambdaQueryWrapper<Anime> wrapper, String sort, boolean asc) {
        switch (sort) {
            case "year" -> wrapper.orderBy(true, asc, Anime::getYear);
            case "douban" -> wrapper.orderBy(true, asc, Anime::getScoreDouban);
            case "imdb", "rt" -> wrapper.orderBy(true, asc, Anime::getId);
            default -> wrapper.orderBy(true, asc, Anime::getUpdatedAt);
        }
    }

    private void applyShortDramaSort(LambdaQueryWrapper<ShortDrama> wrapper, String sort, boolean asc) {
        switch (sort) {
            case "year" -> wrapper.orderBy(true, asc, ShortDrama::getYear);
            case "douban" -> wrapper.orderBy(true, asc, ShortDrama::getScoreDouban);
            case "imdb", "rt" -> wrapper.orderBy(true, asc, ShortDrama::getId);
            default -> wrapper.orderBy(true, asc, ShortDrama::getUpdatedAt);
        }
    }

    /** BigDecimal → Double 安全转换 */
    private Double toDouble(java.math.BigDecimal val) {
        return val != null ? val.doubleValue() : null;
    }

    /** LocalDateTime → 毫秒时间戳 安全转换 */
    private Long toTimestamp(java.time.LocalDateTime dt) {
        return dt != null ? dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null;
    }

    /** 根据排序字段返回比较器 */
    private Comparator<SearchResult> getSearchResultComparator(String sort, boolean desc) {
        Comparator<SearchResult> cmp;
        switch (sort) {
            case "year":
                cmp = Comparator.comparingInt(r -> r.year != null ? r.year : 0);
                break;
            case "imdb":
                cmp = Comparator.comparingDouble(r -> r.ratingImdb != null ? r.ratingImdb : 0);
                break;
            case "rt":
                cmp = Comparator.comparingDouble(r -> r.ratingRT != null ? r.ratingRT : 0);
                break;
            case "douban":
                cmp = Comparator.comparingDouble(r -> r.rating != null ? r.rating : 0);
                break;
            default: // latest - 按更新时间排序
                cmp = Comparator.comparingLong(r -> r.updatedAtMs != null ? r.updatedAtMs : 0);
                break;
        }
        return desc ? cmp.reversed() : cmp;
    }

    // ==================== 内部数据结构 ====================

    /** 搜索结果 */
    public record SearchResult(
            Long id,
            String type,           // movie / drama / variety / anime / short_drama
            String title,
            String cover,
            Integer year,
            Double rating,         // 豆瓣评分
            Double ratingImdb,     // IMDB评分
            Double ratingRT,       // 烂番茄评分
            String summary,
            String director,       // JSON数组字符串
            String actor,          // JSON数组字符串
            String genre,          // JSON数组字符串
            String region,         // JSON数组字符串
            Integer duration,      // 时长（分钟）
            Integer totalEpisode,  // 总集数
            String alias,          // 别名（JSON数组字符串）
            Long updatedAtMs       // 更新时间戳（毫秒）
    ) {}

}
