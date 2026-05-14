package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.UserMovieListItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 用户片单条目数据访问层
 * 提供 user_movie_list_item 表的 CRUD 操作
 */
public interface UserMovieListItemMapper extends BaseMapper<UserMovieListItem> {
}
