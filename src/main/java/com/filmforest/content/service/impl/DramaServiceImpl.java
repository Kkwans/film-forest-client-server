package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.service.DramaService;
import com.filmforest.content.service.ContentTagLookupService;
import com.filmforest.content.service.ContentResourceFilter;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.model.ContentStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
/**
 * 电视剧服务实现
 */
public class DramaServiceImpl extends ServiceImpl<DramaMapper, Drama> implements DramaService {

    private final ContentTagLookupService contentTagLookupService;
    private final ContentResourceFilter contentResourceFilter;

    public DramaServiceImpl(ContentTagLookupService contentTagLookupService,
                            ContentResourceFilter contentResourceFilter) {
        this.contentTagLookupService = contentTagLookupService;
        this.contentResourceFilter = contentResourceFilter;
    }

    @Override
    public IPage<Drama> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                  Integer yearFrom, Integer yearTo, Long tagId, Boolean hasResource, String sortDir) {
        LambdaQueryWrapper<Drama> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Drama::getStatus, ContentStatus.PUBLISHED.code());

        if (year != null) {
            wrapper.eq(Drama::getYear, year);
        } else {
            wrapper.ge(yearFrom != null, Drama::getYear, yearFrom);
            wrapper.le(yearTo != null, Drama::getYear, yearTo);
        }

        wrapper.like(StringUtils.isNotBlank(region), Drama::getRegion, region);
        wrapper.like(StringUtils.isNotBlank(genre), Drama::getGenre, genre);
        contentTagLookupService.apply(wrapper, Drama::getId, tagId, ContentType.DRAMA);
        contentResourceFilter.apply(wrapper, ContentType.DRAMA, hasResource);

        boolean isAsc = "asc".equalsIgnoreCase(sortDir);
        if ("douban".equals(sort)) {
            wrapper.orderBy(true, isAsc, Drama::getScoreDouban);
        } else if ("imdb".equals(sort)) {
            wrapper.orderBy(true, isAsc, Drama::getScoreImdb);
        } else if ("year".equals(sort)) {
            wrapper.orderBy(true, isAsc, Drama::getYear);
        } else {
            wrapper.orderBy(true, isAsc, Drama::getUpdatedAt);
        }

        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Drama getDetail(Long id) {
        return getOne(new LambdaQueryWrapper<Drama>()
                .eq(Drama::getId, id)
                .eq(Drama::getStatus, ContentStatus.PUBLISHED.code()));
    }
}
