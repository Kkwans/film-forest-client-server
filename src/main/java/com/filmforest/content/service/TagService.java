package com.filmforest.content.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.content.dto.PageResult;
import com.filmforest.content.entity.Tag;

import java.util.List;
import java.util.Map;

public interface TagService extends IService<Tag> {

    /**
     * 获取所有标签（按使用次数降序）
     */
    List<Tag> getAllTags();

    /** 获取指定内容类型适用的系统标准题材。 */
    List<Tag> getStandardGenres(String contentType);

    /** 获取标签分页。 */
    IPage<Tag> pageTags(int page, int size);

    /**
     * 获取热门标签 Top N
     */
    List<Tag> getHotTags(int limit);

    /**
     * 创建标签
     */
    Tag createTag(String name, String color);

    /**
     * 更新标签
     */
    Tag updateTag(Long id, String name, String color);

    /**
     * 为内容添加标签
     */
    void addContentTags(Long contentId, String contentType, List<Long> tagIds);

    /**
     * 替换内容的标签（先删后加）
     */
    void setContentTags(Long contentId, String contentType, List<Long> tagIds);

    /**
     * 获取内容的标签列表
     */
    List<Tag> getContentTags(Long contentId, String contentType);

    /**
     * 按标签筛选内容ID列表
     */
    PageResult<Map<String, Object>> getContentIdsByTag(Long tagId, String contentType, int page, int size);

    /**
     * 删除内容的所有标签关联
     */
    void removeContentTags(Long contentId, String contentType);
}
