package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private MovieService movieService;
    @Autowired private DramaService dramaService;
    @Autowired private VarietyService varietyService;
    @Autowired private AnimeService animeService;
    @Autowired private ShortDramaService shortDramaService;

    @Override
    public Map<String, Map<String, List<Map<String, Object>>>> getRecommendations(int topN) {
        Map<String, Map<String, List<Map<String, Object>>>> result = new LinkedHashMap<>();

        // 热门推荐：按豆瓣评分降序
        Map<String, List<Map<String, Object>>> hot = new LinkedHashMap<>();
        hot.put("movie", toList(movieService.list(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
                        .orderByDesc(Movie::getScoreDouban)
                        .last("LIMIT " + topN)), "movie"));
        hot.put("drama", toList(dramaService.list(
                new LambdaQueryWrapper<Drama>()
                        .eq(Drama::getStatus, 1)
                        .orderByDesc(Drama::getScoreDouban)
                        .last("LIMIT " + topN)), "drama"));
        hot.put("variety", toList(varietyService.list(
                new LambdaQueryWrapper<Variety>()
                        .eq(Variety::getStatus, 1)
                        .orderByDesc(Variety::getScoreDouban)
                        .last("LIMIT " + topN)), "variety"));
        hot.put("anime", toList(animeService.list(
                new LambdaQueryWrapper<Anime>()
                        .eq(Anime::getStatus, 1)
                        .orderByDesc(Anime::getScoreDouban)
                        .last("LIMIT " + topN)), "anime"));
        hot.put("short_drama", toList(shortDramaService.list(
                new LambdaQueryWrapper<ShortDrama>()
                        .eq(ShortDrama::getStatus, 1)
                        .orderByDesc(ShortDrama::getScoreDouban)
                        .last("LIMIT " + topN)), "short_drama"));

        // 最新更新：按创建时间降序
        Map<String, List<Map<String, Object>>> latest = new LinkedHashMap<>();
        latest.put("movie", toList(movieService.list(
                new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
                        .orderByDesc(Movie::getCreatedAt)
                        .last("LIMIT " + topN)), "movie"));
        latest.put("drama", toList(dramaService.list(
                new LambdaQueryWrapper<Drama>()
                        .eq(Drama::getStatus, 1)
                        .orderByDesc(Drama::getCreatedAt)
                        .last("LIMIT " + topN)), "drama"));
        latest.put("variety", toList(varietyService.list(
                new LambdaQueryWrapper<Variety>()
                        .eq(Variety::getStatus, 1)
                        .orderByDesc(Variety::getCreatedAt)
                        .last("LIMIT " + topN)), "variety"));
        latest.put("anime", toList(animeService.list(
                new LambdaQueryWrapper<Anime>()
                        .eq(Anime::getStatus, 1)
                        .orderByDesc(Anime::getCreatedAt)
                        .last("LIMIT " + topN)), "anime"));
        latest.put("short_drama", toList(shortDramaService.list(
                new LambdaQueryWrapper<ShortDrama>()
                        .eq(ShortDrama::getStatus, 1)
                        .orderByDesc(ShortDrama::getCreatedAt)
                        .last("LIMIT " + topN)), "short_drama"));

        result.put("hot", hot);
        result.put("latest", latest);
        return result;
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
            } else if (e instanceof Drama d) {
                map.put("id", d.getId());
                map.put("title", d.getTitle());
                map.put("posterUrl", d.getPosterUrl());
                map.put("year", d.getYear());
                map.put("scoreDouban", d.getScoreDouban());
                map.put("genre", d.getGenre());
                map.put("region", d.getRegion());
                map.put("totalEpisode", d.getTotalEpisode());
            } else if (e instanceof Variety v) {
                map.put("id", v.getId());
                map.put("title", v.getTitle());
                map.put("posterUrl", v.getPosterUrl());
                map.put("year", v.getYear());
                map.put("scoreDouban", v.getScoreDouban());
                map.put("genre", v.getGenre());
                map.put("region", v.getRegion());
                map.put("totalEpisode", v.getTotalEpisode());
            } else if (e instanceof Anime a) {
                map.put("id", a.getId());
                map.put("title", a.getTitle());
                map.put("posterUrl", a.getPosterUrl());
                map.put("year", a.getYear());
                map.put("scoreDouban", a.getScoreDouban());
                map.put("genre", a.getGenre());
                map.put("region", a.getRegion());
                map.put("totalEpisode", a.getTotalEpisode());
            } else if (e instanceof ShortDrama s) {
                map.put("id", s.getId());
                map.put("title", s.getTitle());
                map.put("posterUrl", s.getPosterUrl());
                map.put("year", s.getYear());
                map.put("scoreDouban", s.getScoreDouban());
                map.put("genre", s.getGenre());
                map.put("region", s.getRegion());
                map.put("totalEpisode", s.getTotalEpisode());
            }
            return map;
        }).collect(Collectors.toList());
    }
}
