package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.dto.UserListItemPageRow;
import com.filmforest.content.entity.UserMovieListItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 用户片单条目数据访问层
 * 提供 user_movie_list_item 表的 CRUD 操作
 */
public interface UserMovieListItemMapper extends BaseMapper<UserMovieListItem> {

    /**
     * 仅返回已上线内容，并在数据库完成排序和分页。
     */
    List<UserListItemPageRow> selectVisiblePage(@Param("listId") Long listId,
                                                @Param("contentType") String contentType,
                                                @Param("sort") String sort,
                                                @Param("desc") boolean desc,
                                                @Param("limit") int limit,
                                                @Param("offset") long offset);

    /**
     * 统计已上线且属于指定片单的条目数量。
     */
    long countVisible(@Param("listId") Long listId,
                      @Param("contentType") String contentType);
}
