package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.filmforest.content.dto.RelatedVO;
import com.filmforest.content.entity.*;
import com.filmforest.content.mapper.*;
import com.filmforest.content.service.RelatedService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 相关推荐服务实现
 * 策略：同类型标签（genre）重合 → 同地区同年份 → 同类型热门
 */
@Slf4j
@Service
public class RelatedServiceImpl implements RelatedService {

    @Autowired private MovieMapper movieMapper;
    @Autowired private DramaMapper dramaMapper;
    @Autowired private AnimeMapper animeMapper;
    @Autowired private VarietyMapper varietyMapper;
    @Autowired private ShortDramaMapper shortDramaMapper;

    @Override
    public List<RelatedVO> getRelated(String type, Long id, int limit) {
        if (limit <= 0) limit = 6;
        if (limit > 20) limit = 20;

        return switch (type) {
            case "movie" -> queryRelated(movieMapper, "movie", id, limit);
            case "drama" -> queryRelated(dramaMapper, "drama", id, limit);
            case "anime" -> queryRelated(animeMapper, "anime", id, limit);
            case "variety" -> queryRelated(varietyMapper, "variety", id, limit);
            case "short_drama" -> queryRelated(shortDramaMapper, "short_drama", id, limit);
            default -> Collections.emptyList();
        };
    }

    /**
     * 通用相关推荐查询
     * 1. 先尝试同 genre 标签匹配
     * 2. 不够则补充同地区+同年份
     * 3. 还不够则补充同类型热门（评分降序）
     */
    private <T> List<RelatedVO> queryRelated(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                              String type, Long id, int limit) {
        // 获取当前内容
        T current = mapper.selectById(id);
        if (current == null) return Collections.emptyList();

        String genre = getField(current, "genre");
        String region = getField(current, "region");
        Integer year = getIntField(current, "year");

        Set<Long> usedIds = new LinkedHashSet<>();
        List<RelatedVO> result = new ArrayList<>();

        // 策略 1：同 genre 标签
        List<String> genres = parseJsonArray(genre);
        if (!genres.isEmpty()) {
            QueryWrapper<T> wrapper = new QueryWrapper<>();
            wrapper.ne("id", id);
            wrapper.isNotNull("score_douban");
            // 对每个标签做 OR 匹配
            wrapper.and(w -> {
                for (int i = 0; i < Math.min(genres.size(), 3); i++) {
                    if (i == 0) {
                        w.like("genre", genres.get(i));
                    } else {
                        w.or().like("genre", genres.get(i));
                    }
                }
            });
            wrapper.orderByDesc("score_douban");
            wrapper.last("LIMIT " + (limit * 2)); // 多取一些去重
            List<T> genreResults = mapper.selectList(wrapper);
            for (T item : genreResults) {
                if (result.size() >= limit) break;
                Long itemId = getLongField(item, "id");
                if (usedIds.contains(itemId)) continue;
                usedIds.add(itemId);
                result.add(toVO(item, type));
            }
        }

        // 策略 2：同地区 + 同年份（补充）
        if (result.size() < limit) {
            QueryWrapper<T> wrapper = new QueryWrapper<>();
            wrapper.ne("id", id);
            wrapper.isNotNull("score_douban");
            if (StringUtils.isNotBlank(region)) {
                wrapper.like("region", region);
            }
            if (year != null) {
                wrapper.eq("year", year);
            }
            wrapper.orderByDesc("score_douban");
            wrapper.last("LIMIT " + (limit * 2));
            List<T> regionResults = mapper.selectList(wrapper);
            for (T item : regionResults) {
                if (result.size() >= limit) break;
                Long itemId = getLongField(item, "id");
                if (usedIds.contains(itemId)) continue;
                usedIds.add(itemId);
                result.add(toVO(item, type));
            }
        }

        // 策略 3：同类型热门（兜底）
        if (result.size() < limit) {
            QueryWrapper<T> wrapper = new QueryWrapper<>();
            wrapper.ne("id", id);
            wrapper.isNotNull("score_douban");
            wrapper.orderByDesc("score_douban");
            wrapper.last("LIMIT " + (limit * 2));
            List<T> hotResults = mapper.selectList(wrapper);
            for (T item : hotResults) {
                if (result.size() >= limit) break;
                Long itemId = getLongField(item, "id");
                if (usedIds.contains(itemId)) continue;
                usedIds.add(itemId);
                result.add(toVO(item, type));
            }
        }

        return result;
    }

    // ================ 反射工具方法 ================

    @SuppressWarnings("unchecked")
    private <T> String getField(T obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(obj);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Integer getIntField(T obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(obj);
            return val instanceof Integer ? (Integer) val : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Long getLongField(T obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(obj);
            return val instanceof Long ? (Long) val : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> BigDecimal getDecimalField(T obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(obj);
            return val instanceof BigDecimal ? (BigDecimal) val : null;
        } catch (Exception e) {
            return null;
        }
    }

    private <T> RelatedVO toVO(T item, String type) {
        RelatedVO vo = new RelatedVO();
        vo.setId(getLongField(item, "id"));
        vo.setType(type);
        vo.setTitle(getField(item, "title"));
        vo.setPosterUrl(getField(item, "posterUrl"));
        vo.setYear(getIntField(item, "year"));
        BigDecimal score = getDecimalField(item, "scoreDouban");
        vo.setScoreDouban(score != null ? score.doubleValue() : null);
        return vo;
    }

    /**
     * 解析 JSON 数组字符串: ["动作","科幻"] → ["动作", "科幻"]
     */
    private List<String> parseJsonArray(String json) {
        if (StringUtils.isBlank(json)) return Collections.emptyList();
        try {
            String cleaned = json.trim();
            if (cleaned.startsWith("[")) cleaned = cleaned.substring(1);
            if (cleaned.endsWith("]")) cleaned = cleaned.substring(0, cleaned.length() - 1);
            if (cleaned.isBlank()) return Collections.emptyList();

            List<String> result = new ArrayList<>();
            for (String part : cleaned.split(",")) {
                String item = part.trim().replaceAll("^\"|\"$", "").trim();
                if (!item.isEmpty()) result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 genre JSON 失败: {}", json, e);
            return Collections.emptyList();
        }
    }
}
