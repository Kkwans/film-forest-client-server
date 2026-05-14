package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.Movie;

/**
 * 电影服务接口
 * 提供电影列表分页查询（支持多维度筛选和排序）和详情获取
 */
public interface MovieService extends IService<Movie> {

    /**
     * 分页查询电影列表
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
    IPage<Movie> pageList(int pageNum, int pageSize, Integer year, String region, String genre, String sort,
                          Integer yearFrom, Integer yearTo, String sortDir);

    /**
     * 获取电影详情（含在线资源和播放源）
     * @param id 电影ID
     * @return 电影详情，不存在时抛出 BusinessException
     */
    Movie getDetail(Long id);
}
