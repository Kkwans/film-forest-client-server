package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.mapper.VarietyMapper;
import com.filmforest.content.service.VarietyService;
import com.filmforest.content.service.ContentTagLookupService;
import com.filmforest.content.service.ContentResourceFilter;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.model.ContentStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
/**
 * 综艺服务实现
 */
public class VarietyServiceImpl extends ServiceImpl<VarietyMapper, Variety> implements VarietyService {

    private final ContentTagLookupService contentTagLookupService;
    private final ContentResourceFilter contentResourceFilter;

    public VarietyServiceImpl(ContentTagLookupService contentTagLookupService,
                              ContentResourceFilter contentResourceFilter) {
        this.contentTagLookupService = contentTagLookupService;
        this.contentResourceFilter = contentResourceFilter;
    }

    @Override
    public IPage<Variety> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                    Integer yearFrom, Integer yearTo, Long tagId, Boolean hasResource, String sortDir) {
        return pageList(pageNum, pageSize, year, region, genre, sort, yearFrom, yearTo, tagId,
                hasResource, sortDir, null);
    }

    @Override
    public IPage<Variety> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                   Integer yearFrom, Integer yearTo, Long tagId, Boolean hasResource, String sortDir,
                                   String language) {
        Page<Variety> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Variety> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Variety::getStatus, ContentStatus.PUBLISHED.code());

        if (year != null) {
            wrapper.eq(Variety::getYear, year);
        } else {
            wrapper.ge(yearFrom != null, Variety::getYear, yearFrom);
            wrapper.le(yearTo != null, Variety::getYear, yearTo);
        }

        wrapper.like(StringUtils.isNotBlank(region), Variety::getRegion, region);
        wrapper.like(StringUtils.isNotBlank(genre), Variety::getGenre, genre);
        wrapper.like(StringUtils.isNotBlank(language), Variety::getLanguage, language);
        contentTagLookupService.apply(wrapper, Variety::getId, tagId, ContentType.VARIETY);
        contentResourceFilter.apply(wrapper, ContentType.VARIETY, hasResource);

        boolean isAsc = "asc".equalsIgnoreCase(sortDir);
        if ("douban".equals(sort)) {
            wrapper.orderBy(true, isAsc, Variety::getScoreDouban);
        } else if ("imdb".equals(sort)) {
            wrapper.orderBy(true, isAsc, Variety::getScoreDouban); // 综艺只有豆瓣
        } else if ("year".equals(sort)) {
            wrapper.orderBy(true, isAsc, Variety::getYear);
        } else {
            wrapper.orderBy(true, isAsc, Variety::getUpdatedAt);
        }
        wrapper.orderBy(true, isAsc, Variety::getId);

        return page(page, wrapper);
    }

    @Override
    public Variety getDetail(Long id) {
        return getOne(new LambdaQueryWrapper<Variety>()
                .eq(Variety::getId, id)
                .eq(Variety::getStatus, ContentStatus.PUBLISHED.code()));
    }
}
