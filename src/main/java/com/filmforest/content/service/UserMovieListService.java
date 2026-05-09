package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.dto.UserListItemVO;
import com.filmforest.content.entity.UserMovieList;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface UserMovieListService extends IService<UserMovieList> {

    /**
     * 为新用户创建默认片单（想看/在看/看过）
     */
    void createDefaultLists(Long userId);

    /**
     * 获取用户所有片单
     */
    List<UserMovieList> getUserLists(Long userId);

    /**
     * 创建自定义片单
     */
    UserMovieList createList(Long userId, String name, String description);

    /**
     * 更新片单（仅自定义片单可编辑名称）
     */
    void updateList(Long userId, Long listId, String name, String description);

    /**
     * 删除片单（仅自定义片单可删除）
     */
    void deleteList(Long userId, Long listId);

    /**
     * 添加影视到片单（支持评分和备注）
     */
    void addItem(Long userId, Long listId, Long movieId, String contentType, BigDecimal rating, String note);

    /**
     * 从片单移除影视
     */
    void removeItem(Long userId, Long listId, Long movieId, String contentType);

    /**
     * 获取片单内容（分页，包含影视基本信息）
     */
    IPage<UserListItemVO> getListItems(Long userId, Long listId, int pageNum, int pageSize);

    /**
     * 更新片单条目（评分/备注）
     */
    void updateItem(Long userId, Long listId, Long movieId, String contentType, BigDecimal rating, String note);

    /**
     * 查询影视在哪些片单中
     */
    List<Map<String, Object>> getMovieStatus(Long userId, Long movieId, String contentType);
}
