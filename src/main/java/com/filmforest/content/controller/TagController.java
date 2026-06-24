package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 标签管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/tags")
public class TagController {

    @Autowired
    private TagService tagService;

    /**
     * 获取所有标签
     */
    @GetMapping
    public Result<?> getAllTags() {
        List<Tag> tags = tagService.getAllTags();
        return Result.ok(tags);
    }

    /**
     * 获取热门标签
     */
    @GetMapping("/hot")
    public Result<?> getHotTags(@RequestParam(defaultValue = "20") int limit) {
        List<Tag> tags = tagService.getHotTags(limit);
        return Result.ok(tags);
    }

    /**
     * 创建标签
     */
    @PostMapping
    public Result<?> createTag(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String color = (String) body.get("color");
        if (name == null || name.trim().isEmpty()) {
            return Result.fail(400, "标签名称不能为空");
        }
        try {
            Tag tag = tagService.createTag(name.trim(), color);
            return Result.ok(tag);
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public Result<?> updateTag(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String color = (String) body.get("color");
        try {
            Tag tag = tagService.updateTag(id, name, color);
            return Result.ok(tag);
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteTag(@PathVariable Long id) {
        tagService.removeById(id);
        return Result.ok();
    }

    /**
     * 获取内容的标签
     */
    @GetMapping("/content/{contentType}/{contentId}")
    public Result<?> getContentTags(
            @PathVariable String contentType,
            @PathVariable Long contentId) {
        List<Tag> tags = tagService.getContentTags(contentId, contentType);
        return Result.ok(tags);
    }

    /**
     * 设置内容的标签（替换模式）
     */
    @PutMapping("/content/{contentType}/{contentId}")
    public Result<?> setContentTags(
            @PathVariable String contentType,
            @PathVariable Long contentId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> tagIdNums = (List<Number>) body.get("tagIds");
        List<Long> tagIds = tagIdNums != null
                ? tagIdNums.stream().map(Number::longValue).toList()
                : List.of();
        tagService.setContentTags(contentId, contentType, tagIds);
        return Result.ok();
    }

    /**
     * 按标签筛选内容
     */
    @GetMapping("/{tagId}/content")
    public Result<?> getContentByTag(
            @PathVariable Long tagId,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Map<String, Object>> items = tagService.getContentIdsByTag(tagId, contentType, page, size);
        return Result.ok(items);
    }
}
