package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.service.MovieService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements MovieService {

    @Override
    public IPage<Movie> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                  Integer yearFrom, Integer yearTo, String sortDir) {
        Page<Movie> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();

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

        return page(page, wrapper);
    }

    @Override
    public Movie getDetail(Long id) {
        return getById(id);
    }
}
