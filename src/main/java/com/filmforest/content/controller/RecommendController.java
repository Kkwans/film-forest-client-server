package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.service.RecommendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 首页推荐接口
 * 提供热门推荐和最新更新的聚合数据
 */
@Slf4j
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    @Autowired
    private RecommendService recommendService;

    /**
     * 获取首页推荐数据
     * @param topN 每个分类返回的条目数，默认 5
     * @return 热门 + 最新，按类型分组
     */
    @GetMapping
    public Result<?> recommend(
            @RequestParam(defaultValue = "5") int topN) {
        log.info("[Recommend] topN={}", topN);
        Map<String, Map<String, java.util.List<java.util.Map<String, Object>>>> data =
                recommendService.getRecommendations(topN);
        return Result.ok(data);
    }
}
