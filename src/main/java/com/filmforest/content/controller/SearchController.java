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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
