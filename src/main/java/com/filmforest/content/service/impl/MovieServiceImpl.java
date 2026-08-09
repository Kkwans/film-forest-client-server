package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.service.MovieService;
import com.filmforest.content.service.ContentTagLookupService;
import com.filmforest.content.service.ContentResourceFilter;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.model.ContentStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
/**
 * 电影服务实现
 */
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements MovieService {

    private final ContentTagLookupService contentTagLookupService;
    private final ContentResourceFilter contentResourceFilter;

    public MovieServiceImpl(ContentTagLookupService contentTagLookupService,
                            ContentResourceFilter contentResourceFilter) {
        this.contentTagLookupService = contentTagLookupService;
        this.contentResourceFilter = contentResourceFilter;
    }

    @Override
    public IPage<Movie> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                  Integer yearFrom, Integer yearTo, Long tagId, Boolean hasResource, String sortDir) {
        Page<Movie> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Movie::getStatus, ContentStatus.PUBLISHED.code());

        // 年份筛选：精确年份 或 年份范围
        if (year != null) {
            wrapper.eq(Movie::getYear, year);
        } else {
            wrapper.ge(yearFrom != null, Movie::getYear, yearFrom);
            wrapper.le(yearTo != null, Movie::getYear, yearTo);
        }

        // 地区筛选（模糊匹配 JSON 字符串）
        wrapper.like(StringUtils.isNotBlank(region), Movie::getRegion, region);

        // 类型筛选（模糊匹配 JSON 字符串）
        wrapper.like(StringUtils.isNotBlank(genre), Movie::getGenre, genre);
        contentTagLookupService.apply(wrapper, Movie::getId, tagId, ContentType.MOVIE);
        contentResourceFilter.apply(wrapper, ContentType.MOVIE, hasResource);

        // 排序
        boolean isAsc = "asc".equalsIgnoreCase(sortDir);
        if ("douban".equals(sort)) {
            wrapper.orderBy(true, isAsc, Movie::getScoreDouban);
        } else if ("imdb".equals(sort)) {
            wrapper.orderBy(true, isAsc, Movie::getScoreImdb);
        } else if ("year".equals(sort)) {
            wrapper.orderBy(true, isAsc, Movie::getYear);
        } else {
            // 默认按更新时间（最新更新）
            wrapper.orderBy(true, isAsc, Movie::getUpdatedAt);
        }
        wrapper.orderBy(true, isAsc, Movie::getId);

        return page(page, wrapper);
    }

    @Override
    public Movie getDetail(Long id) {
        return getOne(new LambdaQueryWrapper<Movie>()
                .eq(Movie::getId, id)
                .eq(Movie::getStatus, ContentStatus.PUBLISHED.code()));
    }
}
