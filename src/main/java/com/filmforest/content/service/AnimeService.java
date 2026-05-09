package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.Anime;

public interface AnimeService extends IService<Anime> {

    IPage<Anime> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                          Integer yearFrom, Integer yearTo, String sortDir);

    Anime getDetail(Long id);
}
