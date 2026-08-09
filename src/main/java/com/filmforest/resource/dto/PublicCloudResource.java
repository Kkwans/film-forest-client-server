package com.filmforest.resource.dto;

import com.filmforest.resource.entity.ResourceCloud;

import java.time.LocalDateTime;

/** 用户端网盘资源稳定投影，不暴露爬虫内部字段。 */
public record PublicCloudResource(
        Long id,
        String title,
        String url,
        String diskType,
        String password,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PublicCloudResource from(ResourceCloud resource) {
        return new PublicCloudResource(
                resource.getId(), resource.getTitle(), resource.getUrl(), resource.getDiskType(),
                resource.getPassword(), resource.getCreatedAt(), resource.getUpdatedAt());
    }
}
