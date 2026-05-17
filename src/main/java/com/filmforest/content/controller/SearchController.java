package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
/**
 * 全局搜索接口
 * 支持电影/剧集/综艺/动漫/短剧的跨类型搜索，统一排序和分页
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired private MovieService movieService;
    @Autowired private DramaService dramaService;
    @Autowired private VarietyService varietyService;
    @Autowired private AnimeService animeService;
    @Autowired private ShortDramaService shortDramaService;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
        suggestFromTable(movieService, Movie::getTitle, Movie::getAlias, kw, perTableLimit, seen);
        suggestFromTable(dramaService, Drama::getTitle, Drama::getAlias, kw, perTableLimit, seen);
        suggestFromTable(varietyService, Variety::getTitle, Variety::getAlias, kw, perTableLimit, seen);
        suggestFromTable(animeService, Anime::getTitle, Anime::getAlias, kw, perTableLimit, seen);
        suggestFromTable(shortDramaService, ShortDrama::getTitle, ShortDrama::getAlias, kw, perTableLimit, seen);

        // 取前 10 个
        List<String> suggestions = seen.stream().limit(10).collect(Collectors.toList());
        return Result.ok(suggestions);
    }

    /**
     * 热门搜索：返回各类型评分最高的内容标题 Top 10
     * 用评分最高的内容作为"热门搜索词"（无需额外搜索日志表）
     */
    @GetMapping("/hot")
    public Result<?> hotSearch() {
        log.debug("[Search] hot search");
        int perTableLimit = 4;
        List<Map<String, Object>> hotItems = new ArrayList<>();

        // 从各类型取评分 Top N
        addHotItems(movieService.list(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
                        .orderByDesc(Movie::getScoreDouban)
                        .last("LIMIT " + perTableLimit)), hotItems, "movie");
        addHotItems(dramaService.list(
                new LambdaQueryWrapper<Drama>()
                        .eq(Drama::getStatus, 1)
                        .orderByDesc(Drama::getScoreDouban)
                        .last("LIMIT " + perTableLimit)), hotItems, "drama");
        addHotItems(animeService.list(
                new LambdaQueryWrapper<Anime>()
                        .eq(Anime::getStatus, 1)
                        .orderByDesc(Anime::getScoreDouban)
                        .last("LIMIT " + perTableLimit)), hotItems, "anime");

        // 按评分排序取 top 10
        hotItems.sort((a, b) -> {
            Double sa = (Double) a.getOrDefault("score", 0.0);
            Double sb = (Double) b.getOrDefault("score", 0.0);
            return Double.compare(sb, sa);
        });

        List<Map<String, Object>> result = hotItems.stream().limit(10).collect(Collectors.toList());
        return Result.ok(result);
    }

    // ==================== suggest 辅助方法 ====================

    /** 从单表查询标题前缀匹配 */
    private <T> void suggestFromTable(
            com.baomidou.mybatisplus.extension.service.IService<T> service,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> titleField,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> aliasField,
            String keyword, int limit, Set<String> seen) {
        try {
            Page<T> p = service.page(new Page<>(1, limit),
                    new LambdaQueryWrapper<T>()
                            .like(titleField, keyword)
                            .or()
                            .like(aliasField, keyword));
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

    /** 添加热门条目 */
    private void addHotItems(List<?> entities, List<Map<String, Object>> hotItems, String type) {
        for (Object entity : entities) {
            try {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", type);
                if (entity instanceof Movie m) {
                    item.put("id", m.getId());
                    item.put("title", m.getTitle());
                    item.put("score", m.getScoreDouban() != null ? m.getScoreDouban().doubleValue() : 0.0);
                } else if (entity instanceof Drama d) {
                    item.put("id", d.getId());
                    item.put("title", d.getTitle());
                    item.put("score", d.getScoreDouban() != null ? d.getScoreDouban().doubleValue() : 0.0);
                } else if (entity instanceof Anime a) {
                    item.put("id", a.getId());
                    item.put("title", a.getTitle());
                    item.put("score", a.getScoreDouban() != null ? a.getScoreDouban().doubleValue() : 0.0);
                }
                hotItems.add(item);
            } catch (Exception e) {
                log.error("[Search] hot item 处理异常", e);
            }
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
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "desc") String sortDir) {

        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("[Search] 关键词为空");
            return Result.fail("关键词不能为空");
        }

        String kw = keyword.trim();
        log.debug("[Search] keyword={}, page={}, size={}, sort={}, sortDir={}", kw, page, size, sort, sortDir);
        int from = (page - 1) * size;

        int perTableLimit = Math.max(size, 50);

        List<SearchResult> allResults = new ArrayList<>();

        // 分别从 5 张表搜索（各表独立 try-catch，单表失败不影响其他）
        searchMovies(kw, perTableLimit, allResults);
        searchDramas(kw, perTableLimit, allResults);
        searchVarieties(kw, perTableLimit, allResults);
        searchAnimes(kw, perTableLimit, allResults);
        searchShortDramas(kw, perTableLimit, allResults);

        // 堆排序：只维护 top-(from+size) 个元素
        boolean desc = "desc".equalsIgnoreCase(sortDir);
        Comparator<SearchResult> comparator = getSearchResultComparator(sort, desc);
        int need = from + size;
        PriorityQueue<SearchResult> heap = new PriorityQueue<>(need + 1, comparator);

        for (SearchResult r : allResults) {
            heap.offer(r);
            if (heap.size() > need) {
                heap.poll();
            }
        }

        // 从堆中取出结果（逆序）
        List<SearchResult> sorted = new ArrayList<>(heap.size());
        while (!heap.isEmpty()) {
            sorted.add(heap.poll());
        }
        Collections.reverse(sorted);

        // 分页截取
        int total = allResults.size();
        List<SearchResult> pageData = sorted.stream()
                .skip(from)
                .limit(size)
                .collect(Collectors.toList());

        // 记录搜索日志
        try {
            jdbcTemplate.update(
                "INSERT INTO search_log (keyword, result_count, source, created_at) VALUES (?, ?, 'web', NOW())",
                kw, total);
        } catch (Exception e) {
            log.warn("[Search] 记录搜索日志失败: {}", kw, e);
        }

        return Result.ok(new PageWrap<>(pageData, total, size));
    }

    // ==================== 各类型搜索方法 ====================

    private void searchMovies(String kw, int limit, List<SearchResult> results) {
        try {
            Page<Movie> p = movieService.page(new Page<>(1, limit),
                    new LambdaQueryWrapper<Movie>()
                            .like(Movie::getTitle, kw)
                            .or().like(Movie::getAlias, kw)
                            .or().like(Movie::getActor, kw)
                            .or().like(Movie::getDirector, kw));
            for (Movie m : p.getRecords()) {
                results.add(new SearchResult(
                        m.getId(), "movie", m.getTitle(),
                        m.getPosterUrl(), m.getYear(),
                        toDouble(m.getScoreDouban()), toDouble(m.getScoreImdb()), toDouble(m.getScoreRt()),
                        m.getStoryline(), m.getDirector(), m.getActor(),
                        m.getGenre(), m.getRegion(), m.getDuration(), null, m.getAlias()));
            }
        } catch (Exception e) {
            log.error("[Search] 电影搜索异常: keyword={}", kw, e);
        }
    }

    private void searchDramas(String kw, int limit, List<SearchResult> results) {
        try {
            Page<Drama> p = dramaService.page(new Page<>(1, limit),
                    new LambdaQueryWrapper<Drama>()
                            .like(Drama::getTitle, kw)
                            .or().like(Drama::getAlias, kw)
                            .or().like(Drama::getActor, kw));
            for (Drama d : p.getRecords()) {
                results.add(new SearchResult(
                        d.getId(), "drama", d.getTitle(),
                        d.getPosterUrl(), d.getYear(),
                        toDouble(d.getScoreDouban()), toDouble(d.getScoreImdb()), null,
                        d.getStoryline(), d.getDirector(), d.getActor(),
                        d.getGenre(), d.getRegion(), null, d.getTotalEpisode(), d.getAlias()));
            }
        } catch (Exception e) {
            log.error("[Search] 剧集搜索异常: keyword={}", kw, e);
        }
    }

    private void searchVarieties(String kw, int limit, List<SearchResult> results) {
        try {
            Page<Variety> p = varietyService.page(new Page<>(1, limit),
                    new LambdaQueryWrapper<Variety>()
                            .like(Variety::getTitle, kw)
                            .or().like(Variety::getAlias, kw));
            for (Variety v : p.getRecords()) {
                results.add(new SearchResult(
                        v.getId(), "variety", v.getTitle(),
                        v.getPosterUrl(), v.getYear(),
                        toDouble(v.getScoreDouban()), null, null,
                        v.getStoryline(), v.getDirector(), v.getActor(),
                        v.getGenre(), v.getRegion(), null, v.getTotalEpisode(), v.getAlias()));
            }
        } catch (Exception e) {
            log.error("[Search] 综艺搜索异常: keyword={}", kw, e);
        }
    }

    private void searchAnimes(String kw, int limit, List<SearchResult> results) {
        try {
            Page<Anime> p = animeService.page(new Page<>(1, limit),
                    new LambdaQueryWrapper<Anime>()
                            .like(Anime::getTitle, kw)
                            .or().like(Anime::getAlias, kw)
                            .or().like(Anime::getActor, kw));
            for (Anime a : p.getRecords()) {
                results.add(new SearchResult(
                        a.getId(), "anime", a.getTitle(),
                        a.getPosterUrl(), a.getYear(),
                        toDouble(a.getScoreDouban()), null, null,
                        a.getStoryline(), a.getDirector(), a.getActor(),
                        a.getGenre(), a.getRegion(), null, a.getTotalEpisode(), a.getAlias()));
            }
        } catch (Exception e) {
            log.error("[Search] 动漫搜索异常: keyword={}", kw, e);
        }
    }

    private void searchShortDramas(String kw, int limit, List<SearchResult> results) {
        try {
            Page<ShortDrama> p = shortDramaService.page(new Page<>(1, limit),
                    new LambdaQueryWrapper<ShortDrama>()
                            .like(ShortDrama::getTitle, kw)
                            .or().like(ShortDrama::getAlias, kw));
            for (ShortDrama s : p.getRecords()) {
                results.add(new SearchResult(
                        s.getId(), "short_drama", s.getTitle(),
                        s.getPosterUrl(), s.getYear(),
                        null, null, null,
                        s.getStoryline(), null, null,
                        s.getGenre(), s.getRegion(), null, s.getTotalEpisode(), s.getAlias()));
            }
        } catch (Exception e) {
            log.error("[Search] 短剧搜索异常: keyword={}", kw, e);
        }
    }

    // ==================== 工具方法 ====================

    /** BigDecimal → Double 安全转换 */
    private Double toDouble(java.math.BigDecimal val) {
        return val != null ? val.doubleValue() : null;
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
            default: // latest - 默认按豆瓣评分
                cmp = Comparator.comparingDouble(r -> r.rating != null ? r.rating : 0);
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
            String alias           // 别名（JSON数组字符串）
    ) {}

    /** 分页包装 */
    public record PageWrap<T>(List<T> records, long total, long size) {
        public long getPages() {
            return (total + size - 1) / size;
        }
    }
}
