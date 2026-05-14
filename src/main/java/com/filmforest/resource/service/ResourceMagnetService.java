package com.filmforest.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.resource.entity.ResourceMagnet;

import java.util.List;

/**
 * 磁力链接资源 Service
 */
public interface ResourceMagnetService extends IService<ResourceMagnet> {

    /**
     * 根据内容类型和内容ID查询磁力链接资源列表
     */
    List<ResourceMagnet> listByContent(String contentType, Long contentId);
}
