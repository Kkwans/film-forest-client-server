package com.filmforest.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.resource.entity.ResourceOnline;

import java.util.List;

/**
 * 在线播放资源 Service
 */
public interface ResourceOnlineService extends IService<ResourceOnline> {

    /**
     * 根据内容类型和内容ID查询在线播放资源列表
     */
    List<ResourceOnline> listByContent(String contentType, Long contentId);

    /**
     * 根据内容类型、内容ID和集数查询在线播放资源
     */
    List<ResourceOnline> listByContentAndEpisode(String contentType, Long contentId, Integer season, Integer episodeNumber);
}
