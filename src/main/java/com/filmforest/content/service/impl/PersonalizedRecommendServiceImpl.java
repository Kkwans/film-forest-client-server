package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.content.entity.*;
import com.filmforest.content.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 个性化推荐服务实现
 * 策略：
 * 1. 跨类型查询高评分内容
 * 2. 排除已看过/已收藏的内容
 * 3. 按豆瓣评分降序
 * 4. 各类型均匀分布
 */
@Slf4j
@Service
public class PersonalizedRecommendServiceImpl implements PersonalizedRecommendService {

    @Autowired private MovieService movieService;
    @Autowired private DramaService dramaService;
    @Autowired private VarietyService varietyService;
    @Autowired private AnimeService animeService;
    @Autowired private ShortDramaService shortDramaService;

    @Override
    public List<Map<String, Object>> getPersonalized(String genres, String region, String excludeIds, int limit) {
        if (limit <= 0) limit = 12;
        if (limit > 30) limit = 30;

        Set<Long> excludeIdSet = parseIds(excludeIds);
        List<String> genreList = parseGenres(genres);

        List<Map<String, Object>> allResults = new ArrayList<>();

        allResults.addAll(queryMovies(genreList, region, excludeIdSet, limit * 2));
        allResults.addAll(queryDramas(genreList, region, excludeIdSet, limit * 2));
        allResults.addAll(queryVarieties(genreList, region, excludeIdSet, limit * 2));
        allResults.addAll(queryAnimes(genreList, region, excludeIdSet, limit * 2));
        allResults.addAll(queryShortDramas(genreList, region, excludeIdSet, limit * 2));

        // 按评分降序
        allResults.sort((a, b) -> {
            Double sa = (Double) a.getOrDefault("scoreDouban", 0.0);
            Double sb = (Double) b.getOrDefault("scoreDouban", 0.0);
            return Double.compare(sb, sa);
        });

        // 保证类型均匀
        Map<String, List<Map<String, Object>>> byType = allResults.stream()
                .collect(Collectors.groupingBy(m -> (String) m.get("type"), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> balanced = new ArrayList<>();
        int perType = Math.max(limit / 5 + 2, 4);

        for (var entry : byType.entrySet()) {
            List<Map<String, Object>> items = entry.getValue();
            for (int i = 0; i < Math.min(perType, items.size()) && balanced.size() < limit; i++) {
                balanced.add(items.get(i));
            }
        }

        // 补足
        if (balanced.size() < limit) {
            Set<String> used = balanced.stream()
                    .map(m -> m.get("type") + ":" + m.get("id"))
                    .collect(Collectors.toSet());
            for (Map<String, Object> item : allResults) {
                if (balanced.size() >= limit) break;
                String key = item.get("type") + ":" + item.get("id");
                if (!used.contains(key)) {
                    balanced.add(item);
                    used.add(key);
                }
            }
        }

        return balanced.subList(0, Math.min(limit, balanced.size()));
    }

    private List<Map<String, Object>> queryMovies(List<String> genres, String region,
                                                    Set<Long> excludeIds, int limit) {
        LambdaQueryWrapper<Movie> w = new LambdaQueryWrapper<>();
        w.eq(Movie::getStatus, 1);
        if (!excludeIds.isEmpty()) w.notIn(Movie::getId, excludeIds);
        applyGenreFilter(w, genres, "genre");
        if (StringUtils.isNotBlank(region)) w.like(Movie::getRegion, region);
        w.orderByDesc(Movie::getScoreDouban);
        w.last("LIMIT " + limit);
        return movieService.list(w).stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "movie");
            map.put("id", m.getId());
            map.put("title", m.getTitle());
            map.put("posterUrl", m.getPosterUrl());
            map.put("year", m.getYear());
            map.put("scoreDouban", m.getScoreDouban() != null ? m.getScoreDouban().doubleValue() : null);
            map.put("genre", m.getGenre());
            map.put("region", m.getRegion());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> queryDramas(List<String> genres, String region,
                                                    Set<Long> excludeIds, int limit) {
        LambdaQueryWrapper<Drama> w = new LambdaQueryWrapper<>();
        w.eq(Drama::getStatus, 1);
        if (!excludeIds.isEmpty()) w.notIn(Drama::getId, excludeIds);
        applyGenreFilterD(w, genres);
        if (StringUtils.isNotBlank(region)) w.like(Drama::getRegion, region);
        w.orderByDesc(Drama::getScoreDouban);
        w.last("LIMIT " + limit);
        return dramaService.list(w).stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "drama");
            map.put("id", d.getId());
            map.put("title", d.getTitle());
            map.put("posterUrl", d.getPosterUrl());
            map.put("year", d.getYear());
            map.put("scoreDouban", d.getScoreDouban() != null ? d.getScoreDouban().doubleValue() : null);
            map.put("genre", d.getGenre());
            map.put("region", d.getRegion());
            map.put("totalEpisode", d.getTotalEpisode());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> queryVarieties(List<String> genres, String region,
                                                       Set<Long> excludeIds, int limit) {
        LambdaQueryWrapper<Variety> w = new LambdaQueryWrapper<>();
        w.eq(Variety::getStatus, 1);
        if (!excludeIds.isEmpty()) w.notIn(Variety::getId, excludeIds);
        applyGenreFilterV(w, genres);
        if (StringUtils.isNotBlank(region)) w.like(Variety::getRegion, region);
        w.orderByDesc(Variety::getScoreDouban);
        w.last("LIMIT " + limit);
        return varietyService.list(w).stream().map(v -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "variety");
            map.put("id", v.getId());
            map.put("title", v.getTitle());
            map.put("posterUrl", v.getPosterUrl());
            map.put("year", v.getYear());
            map.put("scoreDouban", v.getScoreDouban() != null ? v.getScoreDouban().doubleValue() : null);
            map.put("genre", v.getGenre());
            map.put("region", v.getRegion());
            map.put("totalEpisode", v.getTotalEpisode());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> queryAnimes(List<String> genres, String region,
                                                    Set<Long> excludeIds, int limit) {
        LambdaQueryWrapper<Anime> w = new LambdaQueryWrapper<>();
        w.eq(Anime::getStatus, 1);
        if (!excludeIds.isEmpty()) w.notIn(Anime::getId, excludeIds);
        applyGenreFilterA(w, genres);
        if (StringUtils.isNotBlank(region)) w.like(Anime::getRegion, region);
        w.orderByDesc(Anime::getScoreDouban);
        w.last("LIMIT " + limit);
        return animeService.list(w).stream().map(a -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "anime");
            map.put("id", a.getId());
            map.put("title", a.getTitle());
            map.put("posterUrl", a.getPosterUrl());
            map.put("year", a.getYear());
            map.put("scoreDouban", a.getScoreDouban() != null ? a.getScoreDouban().doubleValue() : null);
            map.put("genre", a.getGenre());
            map.put("region", a.getRegion());
            map.put("totalEpisode", a.getTotalEpisode());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> queryShortDramas(List<String> genres, String region,
                                                         Set<Long> excludeIds, int limit) {
        LambdaQueryWrapper<ShortDrama> w = new LambdaQueryWrapper<>();
        w.eq(ShortDrama::getStatus, 1);
        if (!excludeIds.isEmpty()) w.notIn(ShortDrama::getId, excludeIds);
        applyGenreFilterS(w, genres);
        if (StringUtils.isNotBlank(region)) w.like(ShortDrama::getRegion, region);
        w.orderByDesc(ShortDrama::getScoreDouban);
        w.last("LIMIT " + limit);
        return shortDramaService.list(w).stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "short_drama");
            map.put("id", s.getId());
            map.put("title", s.getTitle());
            map.put("posterUrl", s.getPosterUrl());
            map.put("year", s.getYear());
            map.put("scoreDouban", s.getScoreDouban() != null ? s.getScoreDouban().doubleValue() : null);
            map.put("genre", s.getGenre());
            map.put("region", s.getRegion());
            map.put("totalEpisode", s.getTotalEpisode());
            return map;
        }).collect(Collectors.toList());
    }

    // ========== Genre filter helpers ==========

    @SuppressWarnings("unchecked")
    private <T> void applyGenreFilter(LambdaQueryWrapper<T> w, List<String> genres, String field) {
        if (genres.isEmpty()) return;
        w.and(inner -> {
            for (int i = 0; i < Math.min(genres.size(), 3); i++) {
                if (i == 0) {
                    inner.like(t -> true, field, genres.get(i));
                } else {
                    inner.or().like(t -> true, field, genres.get(i));
                }
            }
        });
    }

    private void applyGenreFilterD(LambdaQueryWrapper<Drama> w, List<String> genres) {
        if (genres.isEmpty()) return;
        w.and(inner -> {
            for (int i = 0; i < Math.min(genres.size(), 3); i++) {
                if (i == 0) inner.like(Drama::getGenre, genres.get(i));
                else inner.or().like(Drama::getGenre, genres.get(i));
            }
        });
    }

    private void applyGenreFilterV(LambdaQueryWrapper<Variety> w, List<String> genres) {
        if (genres.isEmpty()) return;
        w.and(inner -> {
            for (int i = 0; i < Math.min(genres.size(), 3); i++) {
                if (i == 0) inner.like(Variety::getGenre, genres.get(i));
                else inner.or().like(Variety::getGenre, genres.get(i));
            }
        });
    }

    private void applyGenreFilterA(LambdaQueryWrapper<Anime> w, List<String> genres) {
        if (genres.isEmpty()) return;
        w.and(inner -> {
            for (int i = 0; i < Math.min(genres.size(), 3); i++) {
                if (i == 0) inner.like(Anime::getGenre, genres.get(i));
                else inner.or().like(Anime::getGenre, genres.get(i));
            }
        });
    }

    private void applyGenreFilterS(LambdaQueryWrapper<ShortDrama> w, List<String> genres) {
        if (genres.isEmpty()) return;
        w.and(inner -> {
            for (int i = 0; i < Math.min(genres.size(), 3); i++) {
                if (i == 0) inner.like(ShortDrama::getGenre, genres.get(i));
                else inner.or().like(ShortDrama::getGenre, genres.get(i));
            }
        });
    }

    // ========== Parsers ==========

    private Set<Long> parseIds(String ids) {
        Set<Long> set = new HashSet<>();
        if (StringUtils.isBlank(ids)) return set;
        for (String s : ids.split(",")) {
            try { set.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return set;
    }

    private List<String> parseGenres(String genres) {
        if (StringUtils.isBlank(genres)) return Collections.emptyList();
        return Arrays.stream(genres.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(5)
                .collect(Collectors.toList());
    }
}
