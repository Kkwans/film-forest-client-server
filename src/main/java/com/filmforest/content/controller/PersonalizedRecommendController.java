package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.service.PersonalizedRecommendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 个性化推荐接口
 * 基于用户偏好（观看历史提取的类型/地区）推荐内容
 */
@Slf4j
@RestController
@RequestMapping("/api/recommend")
public class PersonalizedRecommendController {

    @Autowired
    private PersonalizedRecommendService personalizedRecommendService;

    /**
     * 个性化推荐
     * @param genres    用户偏好的类型，逗号分隔（如 "动作,科幻,悬疑"）
     * @param region    用户偏好的地区（可选）
     * @param excludeIds 已看过的内容 ID，逗号分隔（可选，用于去重）
     * @param limit     返回数量，默认 12
     * @return 推荐列表（跨类型混合，按评分降序）
     */
    @GetMapping("/personalized")
    public Result<?> personalized(
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String excludeIds,
            @RequestParam(defaultValue = "12") int limit) {
        log.info("[PersonalizedRecommend] genres={}, region={}, excludeIds={}, limit={}", genres, region, excludeIds, limit);
        List<Map<String, Object>> data = personalizedRecommendService.getPersonalized(genres, region, excludeIds, limit);
        return Result.ok(data);
    }
}
