package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务实现
 * 策略：热门（按豆瓣评分降序）+ 最新（按创建时间降序）
 * 每个分类 Top N
 */
@Slf4j
@Service
public class RecommendServiceImpl implements RecommendService {

    private final MovieService movieService;
    private final DramaService dramaService;
    private final VarietyService varietyService;
    private final AnimeService animeService;
    private final ShortDramaService shortDramaService;
    private final Cache<Integer, Map<String, Map<String, List<Map<String, Object>>>>> cache =
            Caffeine.newBuilder().maximumSize(8).expireAfterWrite(java.time.Duration.ofSeconds(90)).build();

    public RecommendServiceImpl(MovieService movieService, DramaService dramaService,
                                VarietyService varietyService, AnimeService animeService,
                                ShortDramaService shortDramaService) {
        this.movieService = movieService;
        this.dramaService = dramaService;
        this.varietyService = varietyService;
        this.animeService = animeService;
        this.shortDramaService = shortDramaService;
    }

    @Override
    public Map<String, Map<String, List<Map<String, Object>>>> getRecommendations(int topN) {
        int safeTopN = Math.min(12, Math.max(1, topN));
        Map<String, Map<String, List<Map<String, Object>>>> cached = cache.getIfPresent(safeTopN);
        if (cached != null) return cached;
        Map<String, Map<String, List<Map<String, Object>>>> result = new LinkedHashMap<>();

        // 热门推荐：按豆瓣评分降序
        Map<String, List<Map<String, Object>>> hot = new LinkedHashMap<>();
        hot.put("movie", toList(movieService.list(
                new LambdaQueryWrapper<Movie>()
                        .select(Movie::getId, Movie::getTitle, Movie::getPosterUrl, Movie::getYear,
                                Movie::getScoreDouban, Movie::getGenre, Movie::getRegion, Movie::getStoryline)
                        .eq(Movie::getStatus, 1)
                        .orderByDesc(Movie::getScoreDouban)
                        .orderByAsc(Movie::getId)
                        .last("LIMIT " + safeTopN)), "movie"));
        hot.put("drama", toList(dramaService.list(
                new LambdaQueryWrapper<Drama>()
                        .select(Drama::getId, Drama::getTitle, Drama::getPosterUrl, Drama::getYear,
                                Drama::getScoreDouban, Drama::getGenre, Drama::getRegion, Drama::getStoryline, Drama::getTotalEpisode)
                        .eq(Drama::getStatus, 1)
                        .orderByDesc(Drama::getScoreDouban)
                        .orderByAsc(Drama::getId)
                        .last("LIMIT " + safeTopN)), "drama"));
        hot.put("variety", toList(varietyService.list(
                new LambdaQueryWrapper<Variety>()
                        .select(Variety::getId, Variety::getTitle, Variety::getPosterUrl, Variety::getYear,
                                Variety::getScoreDouban, Variety::getGenre, Variety::getRegion, Variety::getStoryline, Variety::getTotalEpisode)
                        .eq(Variety::getStatus, 1)
                        .orderByDesc(Variety::getScoreDouban)
                        .orderByAsc(Variety::getId)
                        .last("LIMIT " + safeTopN)), "variety"));
        hot.put("anime", toList(animeService.list(
                new LambdaQueryWrapper<Anime>()
                        .select(Anime::getId, Anime::getTitle, Anime::getPosterUrl, Anime::getYear,
                                Anime::getScoreDouban, Anime::getGenre, Anime::getRegion, Anime::getStoryline, Anime::getTotalEpisode)
                        .eq(Anime::getStatus, 1)
                        .orderByDesc(Anime::getScoreDouban)
                        .orderByAsc(Anime::getId)
                        .last("LIMIT " + safeTopN)), "anime"));
        hot.put("short_drama", toList(shortDramaService.list(
                new LambdaQueryWrapper<ShortDrama>()
                        .select(ShortDrama::getId, ShortDrama::getTitle, ShortDrama::getPosterUrl, ShortDrama::getYear,
                                ShortDrama::getScoreDouban, ShortDrama::getGenre, ShortDrama::getRegion, ShortDrama::getStoryline, ShortDrama::getTotalEpisode)
                        .eq(ShortDrama::getStatus, 1)
                        .orderByDesc(ShortDrama::getScoreDouban)
                        .orderByAsc(ShortDrama::getId)
                        .last("LIMIT " + safeTopN)), "short_drama"));

        // 最新更新：每类多取一倍，在内存中排除热门区已出现的内容。
        int latestCandidateLimit = safeTopN * 2;
        Map<String, List<Map<String, Object>>> latest = new LinkedHashMap<>();
        latest.put("movie", toList(movieService.list(
                new LambdaQueryWrapper<Movie>()
                        .select(Movie::getId, Movie::getTitle, Movie::getPosterUrl, Movie::getYear,
                                Movie::getScoreDouban, Movie::getGenre, Movie::getRegion, Movie::getStoryline, Movie::getUpdatedAt)
                        .eq(Movie::getStatus, 1)
                        .orderByDesc(Movie::getUpdatedAt).orderByAsc(Movie::getId)
                        .last("LIMIT " + latestCandidateLimit)), "movie"));
        latest.put("drama", toList(dramaService.list(
                new LambdaQueryWrapper<Drama>()
                        .select(Drama::getId, Drama::getTitle, Drama::getPosterUrl, Drama::getYear,
                                Drama::getScoreDouban, Drama::getGenre, Drama::getRegion, Drama::getStoryline, Drama::getTotalEpisode, Drama::getUpdatedAt)
                        .eq(Drama::getStatus, 1)
                        .orderByDesc(Drama::getUpdatedAt).orderByAsc(Drama::getId)
                        .last("LIMIT " + latestCandidateLimit)), "drama"));
        latest.put("variety", toList(varietyService.list(
                new LambdaQueryWrapper<Variety>()
                        .select(Variety::getId, Variety::getTitle, Variety::getPosterUrl, Variety::getYear,
                                Variety::getScoreDouban, Variety::getGenre, Variety::getRegion, Variety::getStoryline, Variety::getTotalEpisode, Variety::getUpdatedAt)
                        .eq(Variety::getStatus, 1)
                        .orderByDesc(Variety::getUpdatedAt).orderByAsc(Variety::getId)
                        .last("LIMIT " + latestCandidateLimit)), "variety"));
        latest.put("anime", toList(animeService.list(
                new LambdaQueryWrapper<Anime>()
                        .select(Anime::getId, Anime::getTitle, Anime::getPosterUrl, Anime::getYear,
                                Anime::getScoreDouban, Anime::getGenre, Anime::getRegion, Anime::getStoryline, Anime::getTotalEpisode, Anime::getUpdatedAt)
                        .eq(Anime::getStatus, 1)
                        .orderByDesc(Anime::getUpdatedAt).orderByAsc(Anime::getId)
                        .last("LIMIT " + latestCandidateLimit)), "anime"));
        latest.put("short_drama", toList(shortDramaService.list(
                new LambdaQueryWrapper<ShortDrama>()
                        .select(ShortDrama::getId, ShortDrama::getTitle, ShortDrama::getPosterUrl, ShortDrama::getYear,
                                ShortDrama::getScoreDouban, ShortDrama::getGenre, ShortDrama::getRegion, ShortDrama::getStoryline, ShortDrama::getTotalEpisode, ShortDrama::getUpdatedAt)
                        .eq(ShortDrama::getStatus, 1)
                        .orderByDesc(ShortDrama::getUpdatedAt).orderByAsc(ShortDrama::getId)
                        .last("LIMIT " + latestCandidateLimit)), "short_drama"));

        latest.replaceAll((type, items) -> withoutDuplicateIds(items, hot.get(type), safeTopN));

        result.put("hot", hot);
        result.put("latest", latest);
        cache.put(safeTopN, result);
        return result;
    }

    static List<Map<String, Object>> withoutDuplicateIds(
            List<Map<String, Object>> candidates,
            List<Map<String, Object>> alreadyShown,
            int limit) {
        Set<Object> shownIds = alreadyShown == null
                ? Collections.emptySet()
                : alreadyShown.stream().map(item -> item.get("id")).collect(Collectors.toSet());
        return candidates.stream()
                .filter(item -> !shownIds.contains(item.get("id")))
                .limit(limit)
                .toList();
    }

    /**
     * 将实体列表转为通用 Map 列表（统一字段名）
     */
    private List<Map<String, Object>> toList(List<?> entities, String type) {
        return entities.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type);
            if (e instanceof Movie m) {
                map.put("id", m.getId());
                map.put("title", m.getTitle());
                map.put("posterUrl", m.getPosterUrl());
                map.put("year", m.getYear());
                map.put("scoreDouban", m.getScoreDouban());
                map.put("genre", m.getGenre());
                map.put("region", m.getRegion());
                map.put("summary", m.getStoryline());
            } else if (e instanceof Drama d) {
                map.put("id", d.getId());
                map.put("title", d.getTitle());
                map.put("posterUrl", d.getPosterUrl());
                map.put("year", d.getYear());
                map.put("scoreDouban", d.getScoreDouban());
                map.put("genre", d.getGenre());
                map.put("region", d.getRegion());
                map.put("summary", d.getStoryline());
                map.put("totalEpisode", d.getTotalEpisode());
            } else if (e instanceof Variety v) {
                map.put("id", v.getId());
                map.put("title", v.getTitle());
                map.put("posterUrl", v.getPosterUrl());
                map.put("year", v.getYear());
                map.put("scoreDouban", v.getScoreDouban());
                map.put("genre", v.getGenre());
                map.put("region", v.getRegion());
                map.put("summary", v.getStoryline());
                map.put("totalEpisode", v.getTotalEpisode());
            } else if (e instanceof Anime a) {
                map.put("id", a.getId());
                map.put("title", a.getTitle());
                map.put("posterUrl", a.getPosterUrl());
                map.put("year", a.getYear());
                map.put("scoreDouban", a.getScoreDouban());
                map.put("genre", a.getGenre());
                map.put("region", a.getRegion());
                map.put("summary", a.getStoryline());
                map.put("totalEpisode", a.getTotalEpisode());
            } else if (e instanceof ShortDrama s) {
                map.put("id", s.getId());
                map.put("title", s.getTitle());
                map.put("posterUrl", s.getPosterUrl());
                map.put("year", s.getYear());
                map.put("scoreDouban", s.getScoreDouban());
                map.put("genre", s.getGenre());
                map.put("region", s.getRegion());
                map.put("summary", s.getStoryline());
                map.put("totalEpisode", s.getTotalEpisode());
            }
            return map;
        }).collect(Collectors.toList());
    }
}
