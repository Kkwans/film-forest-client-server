package com.filmforest.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.service.ResourceMagnetService;
import com.filmforest.content.model.ContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 磁力链接资源 Service 实现
 */
@Service
@RequiredArgsConstructor
public class ResourceMagnetServiceImpl extends ServiceImpl<ResourceMagnetMapper, ResourceMagnet>
        implements ResourceMagnetService {

    @Override
    public List<ResourceMagnet> listByContent(String contentType, Long contentId) {
        String canonicalContentType = ContentType.parse(contentType).code();
        return list(new QueryWrapper<ResourceMagnet>()
                .eq("content_type", canonicalContentType)
                .eq("content_id", contentId)
                .eq("enabled", 1)
                .isNull("removed_at")
                .orderByAsc("sort"));
    }
}
