package com.filmforest.resource.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.service.ResourceCloudService;
import com.filmforest.resource.service.ResourceMagnetService;
import com.filmforest.resource.service.ResourceOnlineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资源管理 Controller（在线播放/磁力/网盘）
 */
@Slf4j
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    @Autowired
    private ResourceOnlineService resourceOnlineService;

    @Autowired
    private ResourceMagnetService resourceMagnetService;

    @Autowired
    private ResourceCloudService resourceCloudService;

    // ==================== 在线播放资源 ====================

    /**
     * 获取在线播放资源列表
     * @param contentType movie/drama/variety/anime/short
     * @param contentId 内容ID
     * @param season 季（可选，默认1）
     * @param episodeNumber 集号（可选）
     */
    @GetMapping("/online")
    public Result<List<ResourceOnline>> listOnline(
            @RequestParam String contentType,
            @RequestParam Long contentId,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Integer episodeNumber) {
        log.debug("[Resource] 查询在线播放资源: contentType={}, contentId={}, season={}, episode={}",
                contentType, contentId, season, episodeNumber);
        List<ResourceOnline> list;
        if (season != null || episodeNumber != null) {
            list = resourceOnlineService.listByContentAndEpisode(contentType, contentId, season, episodeNumber);
        } else {
            list = resourceOnlineService.listByContent(contentType, contentId);
        }
        return Result.ok(list);
    }

    // ==================== 磁力链接资源 ====================

    /**
     * 获取磁力链接资源列表
     */
    @GetMapping("/magnet")
    public Result<List<ResourceMagnet>> listMagnet(
            @RequestParam String contentType,
            @RequestParam Long contentId) {
        log.debug("[Resource] 查询磁力链接: contentType={}, contentId={}", contentType, contentId);
        return Result.ok(resourceMagnetService.listByContent(contentType, contentId));
    }

    // ==================== 网盘资源 ====================

    /**
     * 获取网盘资源列表
     */
    @GetMapping("/cloud")
    public Result<List<ResourceCloud>> listCloud(
            @RequestParam String contentType,
            @RequestParam Long contentId) {
        log.debug("[Resource] 查询网盘资源: contentType={}, contentId={}", contentType, contentId);
        return Result.ok(resourceCloudService.listByContent(contentType, contentId));
    }
}
