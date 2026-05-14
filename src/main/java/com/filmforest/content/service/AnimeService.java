package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.Anime;

/**
 * 动漫服务接口
 * 提供动漫列表分页查询（支持多维度筛选和排序）和详情获取
 */
public interface AnimeService extends IService<Anime> {

    /**
     * 分页查询动漫列表
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param year 年份筛选
     * @param region 地区筛选
     * @param genre 类型筛选
     * @param sort 排序字段（score/year）
     * @param yearFrom 起始年份
     * @param yearTo 截止年份
     * @param sortDir 排序方向（asc/desc）
     * @return 分页结果
     */
    IPage<Anime> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                          Integer yearFrom, Integer yearTo, String sortDir);

    /**
     * 获取动漫详情（含在线资源和播放源）
     * @param id 动漫ID
     * @return 动漫详情，不存在时抛出 BusinessException
     */
    Anime getDetail(Long id);
}
