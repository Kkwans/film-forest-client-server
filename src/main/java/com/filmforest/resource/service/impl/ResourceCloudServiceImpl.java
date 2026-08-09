package com.filmforest.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.service.ResourceCloudService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 网盘链接资源 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ResourceCloudServiceImpl extends ServiceImpl<ResourceCloudMapper, ResourceCloud>
        implements ResourceCloudService {

    @Override
    public List<ResourceCloud> listByContent(String contentType, Long contentId) {
        return list(new QueryWrapper<ResourceCloud>()
                .eq("content_type", contentType)
                .eq("content_id", contentId)
                .eq("enabled", 1)
                .isNull("removed_at")
                .orderByAsc("sort"));
    }
}
