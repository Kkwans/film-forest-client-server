package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.mapper.AnimeMapper;
import com.filmforest.content.service.AnimeService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class AnimeServiceImpl extends ServiceImpl<AnimeMapper, Anime> implements AnimeService {

    @Override
    public IPage<Anime> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                  Integer yearFrom, Integer yearTo, String sortDir) {
        Page<Anime> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Anime> wrapper = new LambdaQueryWrapper<>();

        if (year != null) {
            wrapper.eq(Anime::getYear, year);
        } else {
            wrapper.ge(yearFrom != null, Anime::getYear, yearFrom);
            wrapper.le(yearTo != null, Anime::getYear, yearTo);
        }

        wrapper.like(StringUtils.isNotBlank(region), Anime::getRegion, region);
        wrapper.like(StringUtils.isNotBlank(genre), Anime::getGenre, genre);

        boolean isAsc = "asc".equalsIgnoreCase(sortDir);
        if ("douban".equals(sort)) {
            wrapper.orderBy(true, isAsc, Anime::getScoreDouban);
        } else if ("imdb".equals(sort)) {
            wrapper.orderBy(true, isAsc, Anime::getScoreDouban);
        } else if ("year".equals(sort)) {
            wrapper.orderBy(true, isAsc, Anime::getYear);
        } else {
            wrapper.orderByDesc(Anime::getCreatedAt);
        }

        return page(page, wrapper);
    }

    @Override
    public Anime getDetail(Long id) {
        return getById(id);
    }
}
