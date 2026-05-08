package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.mapper.VarietyMapper;
import com.filmforest.content.service.VarietyService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class VarietyServiceImpl extends ServiceImpl<VarietyMapper, Variety> implements VarietyService {

    @Override
    public IPage<Variety> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                                    Integer yearFrom, Integer yearTo, String sortDir) {
        Page<Variety> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Variety> wrapper = new LambdaQueryWrapper<>();

        if (year != null) {
            wrapper.eq(Variety::getYear, year);
        } else {
            wrapper.ge(yearFrom != null, Variety::getYear, yearFrom);
            wrapper.le(yearTo != null, Variety::getYear, yearTo);
        }

        wrapper.like(StringUtils.isNotBlank(region), Variety::getRegion, region);
        wrapper.like(StringUtils.isNotBlank(genre), Variety::getGenre, genre);

        boolean isAsc = "asc".equalsIgnoreCase(sortDir);
        if ("douban".equals(sort)) {
            wrapper.orderBy(true, isAsc, Variety::getScoreDouban);
        } else if ("imdb".equals(sort)) {
            wrapper.orderBy(true, isAsc, Variety::getScoreDouban); // 综艺只有豆瓣
        } else if ("year".equals(sort)) {
            wrapper.orderBy(true, isAsc, Variety::getYear);
        } else {
            wrapper.orderByDesc(Variety::getCreatedAt);
        }

        return page(page, wrapper);
    }

    @Override
    public Variety getDetail(Long id) {
        return getById(id);
    }
}
