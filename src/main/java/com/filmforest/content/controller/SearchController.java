package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局搜索接口
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private MovieService movieService;

    @Autowired
    private DramaService dramaService;

    @Autowired
    private VarietyService varietyService;

    @Autowired
    private AnimeService animeService;

    @Autowired
    private ShortDramaService shortDramaService;

    /**
     * 搜索接口（合并电影/剧集/综艺/动漫/短剧）
     * 使用 SQL 分页查询，避免全表加载到内存
     */
    @GetMapping
    public Result<?> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.fail("关键词不能为空");
        }
        
        String kw = keyword.trim();
        int from = (page - 1) * size;
        int perTableLimit = Math.max(size, 50);
        
        List<SearchResult> allResults = new ArrayList<>();
        
        // 电影：标题/别名/演员/导演
        try {
            Page<Movie> moviePage = movieService.page(
                    new Page<>(1, perTableLimit),
                    new LambdaQueryWrapper<Movie>()
                            .like(Movie::getTitle, kw)
                            .or().like(Movie::getAlias, kw)
                            .or().like(Movie::getActor, kw)
                            .or().like(Movie::getDirector, kw)
            );
            moviePage.getRecords().forEach(m -> allResults.add(new SearchResult(
                    m.getId(), "movie", m.getTitle(),
                    m.getPosterUrl(), m.getYear(),
                    m.getScoreDouban() != null ? m.getScoreDouban().doubleValue() : null,
                    m.getScoreImdb() != null ? m.getScoreImdb().doubleValue() : null,
                    null,
                    m.getStoryline(),
                    m.getDirector(),
                    m.getActor(),
                    m.getGenre(),
                    m.getRegion(),
                    m.getDuration(),
                    null,
                    m.getAlias()
            )));
        } catch (Exception e) {
            // 搜索失败不影响其他类型
        }
        
        // 剧集：标题/别名/演员
        try {
            Page<Drama> dramaPage = dramaService.page(
                    new Page<>(1, perTableLimit),
                    new LambdaQueryWrapper<Drama>()
                            .like(Drama::getTitle, kw)
                            .or().like(Drama::getAlias, kw)
                            .or().like(Drama::getActor, kw)
            );
            dramaPage.getRecords().forEach(d -> allResults.add(new SearchResult(
                    d.getId(), "drama", d.getTitle(),
                    d.getPosterUrl(), d.getYear(),
                    d.getScoreDouban() != null ? d.getScoreDouban().doubleValue() : null,
                    d.getScoreImdb() != null ? d.getScoreImdb().doubleValue() : null,
                    null,
                    d.getStoryline(),
                    d.getDirector(),
                    d.getActor(),
                    d.getGenre(),
                    d.getRegion(),
                    null,
                    d.getTotalEpisode(),
                    d.getAlias()
            )));
        } catch (Exception e) { }
        
        // 综艺：标题/别名
        try {
            Page<Variety> varietyPage = varietyService.page(
                    new Page<>(1, perTableLimit),
                    new LambdaQueryWrapper<Variety>()
                            .like(Variety::getTitle, kw)
                            .or().like(Variety::getAlias, kw)
            );
            varietyPage.getRecords().forEach(v -> allResults.add(new SearchResult(
                    v.getId(), "variety", v.getTitle(),
                    v.getPosterUrl(), v.getYear(),
                    v.getScoreDouban() != null ? v.getScoreDouban().doubleValue() : null,
                    null, null,
                    v.getStoryline(),
                    v.getDirector(),
                    v.getActor(),
                    v.getGenre(),
                    v.getRegion(),
                    null,
                    v.getTotalEpisode(),
                    v.getAlias()
            )));
        } catch (Exception e) { }
        
        // 动漫：标题/别名/演员
        try {
            Page<Anime> animePage = animeService.page(
                    new Page<>(1, perTableLimit),
                    new LambdaQueryWrapper<Anime>()
                            .like(Anime::getTitle, kw)
                            .or().like(Anime::getAlias, kw)
                            .or().like(Anime::getActor, kw)
            );
            animePage.getRecords().forEach(a -> allResults.add(new SearchResult(
                    a.getId(), "anime", a.getTitle(),
                    a.getPosterUrl(), a.getYear(),
                    a.getScoreDouban() != null ? a.getScoreDouban().doubleValue() : null,
                    null,
                    null,
                    a.getStoryline(),
                    a.getDirector(),
                    a.getActor(),
                    a.getGenre(),
                    a.getRegion(),
                    null,
                    a.getTotalEpisode(),
                    a.getAlias()
            )));
        } catch (Exception e) { }
        
        // 短剧：标题/别名
        try {
            Page<ShortDrama> shortPage = shortDramaService.page(
                    new Page<>(1, perTableLimit),
                    new LambdaQueryWrapper<ShortDrama>()
                            .like(ShortDrama::getTitle, kw)
                            .or().like(ShortDrama::getAlias, kw)
            );
            shortPage.getRecords().forEach(s -> allResults.add(new SearchResult(
                    s.getId(), "short_drama", s.getTitle(),
                    s.getPosterUrl(), s.getYear(),
                    null, null, null,
                    s.getStoryline(),
                    null,
                    null,
                    s.getGenre(),
                    s.getRegion(),
                    null,
                    s.getTotalEpisode(),
                    s.getAlias()
            )));
        } catch (Exception e) { }
        
        // 按评分降序排序（有评分的优先）
        allResults.sort((a, b) -> {
            Double ra = a.rating != null ? a.rating : 0.0;
            Double rb = b.rating != null ? b.rating : 0.0;
            return Double.compare(rb, ra);
        });
        
        // 简单分页
        int total = allResults.size();
        List<SearchResult> pageData = allResults.stream()
                .skip(from)
                .limit(size)
                .collect(Collectors.toList());
        
        PageWrap<SearchResult> pageWrap = new PageWrap<>(pageData, total, size);
        return Result.ok(pageWrap);
    }

    // 内部类：搜索结果
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

    // 内部类：分页包装
    public record PageWrap<T>(List<T> records, long total, long size) {
        public long getPages() { return (total + size - 1) / size; }
    }
}
