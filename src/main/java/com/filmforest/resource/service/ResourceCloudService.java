package com.filmforest.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.resource.entity.ResourceCloud;

import java.util.List;

/**
 * 网盘链接资源 Service
 */
public interface ResourceCloudService extends IService<ResourceCloud> {

    /**
     * 根据内容类型和内容ID查询网盘资源列表
     */
    List<ResourceCloud> listByContent(String contentType, Long contentId);
}
