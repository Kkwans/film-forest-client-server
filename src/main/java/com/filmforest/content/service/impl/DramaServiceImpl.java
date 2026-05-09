package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.service.DramaService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class DramaServiceImpl extends ServiceImpl<DramaMapper, Drama> implements DramaService {

    @Override
    public IPage<Drama> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                  Integer yearFrom, Integer yearTo, String sortDir) {
        LambdaQueryWrapper<Drama> wrapper = new LambdaQueryWrapper<>();

        if (year != null) {
            wrapper.eq(Drama::getYear, year);
        } else {
            wrapper.ge(yearFrom != null, Drama::getYear, yearFrom);
            wrapper.le(yearTo != null, Drama::getYear, yearTo);
        }

        wrapper.like(StringUtils.isNotBlank(region), Drama::getRegion, region);
        wrapper.like(StringUtils.isNotBlank(genre), Drama::getGenre, genre);

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
        return getById(id);
    }
}
