package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.filmforest.content.dto.RelatedVO;
import com.filmforest.content.entity.*;
import com.filmforest.content.mapper.*;
import com.filmforest.content.model.ContentStatus;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.RelatedService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 相关推荐服务实现
 * 策略：标准题材重合 → 同地区同年份 → 同类型热门
 */
@Slf4j
@Service
public class RelatedServiceImpl implements RelatedService {

    private final MovieMapper movieMapper;
    private final DramaMapper dramaMapper;
    private final AnimeMapper animeMapper;
    private final VarietyMapper varietyMapper;
    private final ShortDramaMapper shortDramaMapper;
    private final ContentTagMapper contentTagMapper;

    public RelatedServiceImpl(MovieMapper movieMapper,
                              DramaMapper dramaMapper,
                              AnimeMapper animeMapper,
                              VarietyMapper varietyMapper,
                              ShortDramaMapper shortDramaMapper,
                              ContentTagMapper contentTagMapper) {
        this.movieMapper = movieMapper;
        this.dramaMapper = dramaMapper;
        this.animeMapper = animeMapper;
        this.varietyMapper = varietyMapper;
        this.shortDramaMapper = shortDramaMapper;
        this.contentTagMapper = contentTagMapper;
    }

    @Override
    public List<RelatedVO> getRelated(String type, Long id, int limit) {
        if (limit <= 0) limit = 6;
        if (limit > 20) limit = 20;

        final ContentType contentType;
        try {
            contentType = ContentType.parse(type);
        } catch (RuntimeException unsupported) {
            return Collections.emptyList();
        }

        return switch (contentType) {
            case MOVIE -> queryRelated(movieMapper, contentType, id, limit);
            case DRAMA -> queryRelated(dramaMapper, contentType, id, limit);
            case ANIME -> queryRelated(animeMapper, contentType, id, limit);
            case VARIETY -> queryRelated(varietyMapper, contentType, id, limit);
            case SHORT_DRAMA -> queryRelated(shortDramaMapper, contentType, id, limit);
        };
    }

    /**
     * 通用相关推荐查询
     * 1. 先按标准 content_tag 题材重合数匹配
     * 2. 不够则补充同地区+同年份
     * 3. 还不够则补充同类型热门（评分降序）
     */
    private <T> List<RelatedVO> queryRelated(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                              ContentType contentType, Long id, int limit) {
        // 获取当前内容
        T current = mapper.selectOne(new QueryWrapper<T>()
                .eq("id", id)
                .eq("status", ContentStatus.PUBLISHED.code()));
        if (current == null) return Collections.emptyList();

        String region = getField(current, "region");
        Integer year = getIntField(current, "year");

        Set<Long> usedIds = new LinkedHashSet<>();
        usedIds.add(id);
        List<RelatedVO> result = new ArrayList<>();

        List<Long> rankedTagCandidateIds = findRankedTagCandidates(contentType, id, limit * 4);
        if (!rankedTagCandidateIds.isEmpty()) {
            QueryWrapper<T> wrapper = visibleContentWrapper();
            wrapper.in("id", rankedTagCandidateIds);
            Map<Long, T> byId = new HashMap<>();
            mapper.selectList(wrapper).forEach(item -> byId.put(getLongField(item, "id"), item));
            for (Long candidateId : rankedTagCandidateIds) {
                T item = byId.get(candidateId);
                if (item == null) continue;
                appendIfNew(result, usedIds, item, contentType.code(), limit);
            }
        }

        // 策略 2：同地区 + 同年份（补充）
        if (result.size() < limit) {
            QueryWrapper<T> wrapper = new QueryWrapper<>();
            wrapper.eq("status", ContentStatus.PUBLISHED.code());
            wrapper.notIn("id", usedIds);
            List<String> regions = parseJsonArray(region);
            if (!regions.isEmpty()) {
                wrapper.and(nested -> {
                    for (int index = 0; index < regions.size(); index++) {
                        if (index == 0) nested.like("region", regions.get(index));
                        else nested.or().like("region", regions.get(index));
                    }
                });
            }
            if (year != null) {
                wrapper.eq("year", year);
            }
            wrapper.orderByDesc("score_douban");
            wrapper.last("LIMIT " + (limit * 2));
            List<T> regionResults = mapper.selectList(wrapper);
            for (T item : regionResults) {
                if (result.size() >= limit) break;
                appendIfNew(result, usedIds, item, contentType.code(), limit);
            }
        }

        // 策略 3：同类型热门（兜底）
        if (result.size() < limit) {
            QueryWrapper<T> wrapper = new QueryWrapper<>();
            wrapper.eq("status", ContentStatus.PUBLISHED.code());
            wrapper.notIn("id", usedIds);
            wrapper.orderByDesc("score_douban");
            wrapper.orderByDesc("updated_at");
            wrapper.last("LIMIT " + (limit * 2));
            List<T> hotResults = mapper.selectList(wrapper);
            for (T item : hotResults) {
                if (result.size() >= limit) break;
                appendIfNew(result, usedIds, item, contentType.code(), limit);
            }
        }

        return result;
    }

    private List<Long> findRankedTagCandidates(ContentType contentType, Long contentId, int limit) {
        List<Long> tagIds = contentTagMapper.selectList(new QueryWrapper<ContentTag>()
                        .select("tag_id")
                        .eq("content_type", contentType.code())
                        .eq("content_id", contentId))
                .stream()
                .map(ContentTag::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (tagIds.isEmpty()) return List.of();

        return contentTagMapper.selectList(new QueryWrapper<ContentTag>()
                        .select("content_id")
                        .eq("content_type", contentType.code())
                        .in("tag_id", tagIds)
                        .ne("content_id", contentId)
                        .groupBy("content_id")
                        .orderByDesc("COUNT(DISTINCT tag_id)")
                        .orderByDesc("MAX(created_at)")
                        .last("LIMIT " + Math.max(1, limit)))
                .stream()
                .map(ContentTag::getContentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private <T> QueryWrapper<T> visibleContentWrapper() {
        return new QueryWrapper<T>().eq("status", ContentStatus.PUBLISHED.code());
    }

    private <T> void appendIfNew(List<RelatedVO> result,
                                 Set<Long> usedIds,
                                 T item,
                                 String type,
                                 int limit) {
        if (result.size() >= limit) return;
        Long itemId = getLongField(item, "id");
        if (itemId == null || !usedIds.add(itemId)) return;
        result.add(toVO(item, type));
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
