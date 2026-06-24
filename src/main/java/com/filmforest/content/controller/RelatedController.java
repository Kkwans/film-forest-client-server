package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.RelatedVO;
import com.filmforest.content.service.RelatedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 相关推荐接口
 * GET /api/{type}/{id}/related → 同类型相关内容推荐
 */
@Slf4j
@RestController
public class RelatedController {

    @Autowired
    private RelatedService relatedService;

    /**
     * 获取相关推荐
     * @param type 内容类型：movie / drama / anime / variety / short_drama
     * @param id   内容 ID
     * @param limit 返回数量（默认 6，最大 20）
     */
    @GetMapping("/api/{type}/{id}/related")
    public Result<List<RelatedVO>> related(
            @PathVariable String type,
            @PathVariable Long id,
            @RequestParam(defaultValue = "6") int limit) {
        List<RelatedVO> related = relatedService.getRelated(type, id, limit);
        return Result.ok(related);
    }
}
