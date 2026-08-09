package com.filmforest.resource.dto;

import com.filmforest.resource.entity.ResourceOnline;

import java.time.LocalDateTime;

/** 用户端在线播放资源稳定投影。 */
public record PublicOnlineResource(
        Long id,
        String sourceName,
        String sourceUrl,
        String sourcePageUrl,
        String playbackType,
        Integer season,
        Integer episodeNumber,
        String episodeTitle,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PublicOnlineResource from(ResourceOnline resource) {
        return new PublicOnlineResource(
                resource.getId(), resource.getSourceName(), resource.getSourceUrl(),
                resource.getSourcePageUrl(), resource.getPlaybackType(),
                resource.getSeason(), resource.getEpisodeNumber(), resource.getEpisodeTitle(),
                resource.getCreatedAt(), resource.getUpdatedAt());
    }
}
