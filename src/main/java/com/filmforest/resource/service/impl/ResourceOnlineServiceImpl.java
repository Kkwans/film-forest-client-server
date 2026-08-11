package com.filmforest.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import com.filmforest.resource.service.ResourceOnlineService;
import com.filmforest.content.model.ContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在线播放资源 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ResourceOnlineServiceImpl extends ServiceImpl<ResourceOnlineMapper, ResourceOnline>
        implements ResourceOnlineService {

    @Override
    public List<ResourceOnline> listByContent(String contentType, Long contentId) {
        String canonicalContentType = ContentType.parse(contentType).code();
        return list(new QueryWrapper<ResourceOnline>()
                .eq("content_type", canonicalContentType)
                .eq("content_id", contentId)
                .eq("enabled", 1)
                .isNull("removed_at")
                .orderByAsc("sort"));
    }

    @Override
    public List<ResourceOnline> listByContentAndEpisode(String contentType, Long contentId, Integer season, Integer episodeNumber) {
        String canonicalContentType = ContentType.parse(contentType).code();
        return list(new QueryWrapper<ResourceOnline>()
                .eq("content_type", canonicalContentType)
                .eq("content_id", contentId)
                .eq("enabled", 1)
                .isNull("removed_at")
                .eq(season != null, "season", season)
                .eq(episodeNumber != null, "episode_number", episodeNumber)
                .orderByAsc("sort"));
    }
}
