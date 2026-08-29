package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.mapper.AnimeMapper;
import com.filmforest.content.service.AnimeService;
import com.filmforest.content.service.ContentTagLookupService;
import com.filmforest.content.service.ContentResourceFilter;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.model.ContentStatus;
import com.filmforest.content.model.ContentSort;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
/**
 * 动漫服务实现
 */
public class AnimeServiceImpl extends ServiceImpl<AnimeMapper, Anime> implements AnimeService {

    private final ContentTagLookupService contentTagLookupService;
    private final ContentResourceFilter contentResourceFilter;

    public AnimeServiceImpl(ContentTagLookupService contentTagLookupService,
                            ContentResourceFilter contentResourceFilter) {
        this.contentTagLookupService = contentTagLookupService;
        this.contentResourceFilter = contentResourceFilter;
    }

    @Override
    public IPage<Anime> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                  Integer yearFrom, Integer yearTo, Long tagId, Boolean hasResource, String sortDir) {
        return pageList(pageNum, pageSize, year, region, genre, sort, yearFrom, yearTo, tagId,
                hasResource, sortDir, null);
    }

    @Override
    public IPage<Anime> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                 Integer yearFrom, Integer yearTo, Long tagId, Boolean hasResource, String sortDir,
                                 String language) {
        Page<Anime> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Anime> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Anime::getStatus, ContentStatus.PUBLISHED.code());

        if (year != null) {
            wrapper.eq(Anime::getYear, year);
        } else {
            wrapper.ge(yearFrom != null, Anime::getYear, yearFrom);
            wrapper.le(yearTo != null, Anime::getYear, yearTo);
        }

        wrapper.like(StringUtils.isNotBlank(region), Anime::getRegion, region);
        wrapper.like(StringUtils.isNotBlank(genre), Anime::getGenre, genre);
        wrapper.like(StringUtils.isNotBlank(language), Anime::getLanguage, language);
        contentTagLookupService.apply(wrapper, Anime::getId, tagId, ContentType.ANIME);
        contentResourceFilter.apply(wrapper, ContentType.ANIME, hasResource);

        ContentSort contentSort = ContentSort.parse(sort, ContentType.ANIME);
        boolean isAsc = "asc".equalsIgnoreCase(sortDir);
        if (contentSort == ContentSort.DOUBAN) {
            wrapper.orderBy(true, isAsc, Anime::getScoreDouban);
        } else if (contentSort == ContentSort.IMDB) {
            wrapper.orderBy(true, isAsc, Anime::getScoreImdb);
        } else if (contentSort == ContentSort.YEAR) {
            wrapper.orderBy(true, isAsc, Anime::getYear);
        } else {
            wrapper.orderBy(true, isAsc, Anime::getUpdatedAt);
        }
        wrapper.orderBy(true, isAsc, Anime::getId);

        return page(page, wrapper);
    }

    @Override
    public Anime getDetail(Long id) {
        return getOne(new LambdaQueryWrapper<Anime>()
                .eq(Anime::getId, id)
                .eq(Anime::getStatus, ContentStatus.PUBLISHED.code()));
    }
}
