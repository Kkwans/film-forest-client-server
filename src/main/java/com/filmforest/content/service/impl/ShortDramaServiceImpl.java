package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.mapper.ShortDramaMapper;
import com.filmforest.content.service.ShortDramaService;
import com.filmforest.content.service.ContentTagLookupService;
import com.filmforest.content.service.ContentResourceFilter;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.model.ContentStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
/**
 * 短剧服务实现
 */
public class ShortDramaServiceImpl extends ServiceImpl<ShortDramaMapper, ShortDrama> implements ShortDramaService {

    private final ContentTagLookupService contentTagLookupService;
    private final ContentResourceFilter contentResourceFilter;

    public ShortDramaServiceImpl(ContentTagLookupService contentTagLookupService,
                                 ContentResourceFilter contentResourceFilter) {
        this.contentTagLookupService = contentTagLookupService;
        this.contentResourceFilter = contentResourceFilter;
    }

    @Override
    public IPage<ShortDrama> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                       Integer yearFrom, Integer yearTo, Long tagId, Boolean hasResource, String sortDir) {
        Page<ShortDrama> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ShortDrama> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShortDrama::getStatus, ContentStatus.PUBLISHED.code());

        if (year != null) {
            wrapper.eq(ShortDrama::getYear, year);
        } else {
            wrapper.ge(yearFrom != null, ShortDrama::getYear, yearFrom);
            wrapper.le(yearTo != null, ShortDrama::getYear, yearTo);
        }

        wrapper.like(StringUtils.isNotBlank(region), ShortDrama::getRegion, region);
        wrapper.like(StringUtils.isNotBlank(genre), ShortDrama::getGenre, genre);
        contentTagLookupService.apply(wrapper, ShortDrama::getId, tagId, ContentType.SHORT_DRAMA);
        contentResourceFilter.apply(wrapper, ContentType.SHORT_DRAMA, hasResource);

        boolean isAsc = "asc".equalsIgnoreCase(sortDir);
        if ("year".equals(sort)) {
            wrapper.orderBy(true, isAsc, ShortDrama::getYear);
        } else {
            wrapper.orderBy(true, isAsc, ShortDrama::getUpdatedAt);
        }

        return page(page, wrapper);
    }

    @Override
    public ShortDrama getDetail(Long id) {
        return getOne(new LambdaQueryWrapper<ShortDrama>()
                .eq(ShortDrama::getId, id)
                .eq(ShortDrama::getStatus, ContentStatus.PUBLISHED.code()));
    }
}
