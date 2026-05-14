package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.UserMovieList;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 用户片单数据访问层
 * 提供 user_movie_list 表的 CRUD 操作
 */
public interface UserMovieListMapper extends BaseMapper<UserMovieList> {
}
