package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.GenreOption;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.TagService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理接口
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 获取所有标签
     */
    @GetMapping
    public Result<?> getAllTags(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.ok(com.filmforest.content.dto.PageResult.from(tagService.pageTags(page, size)));
    }

    /** 获取当前内容类型可用的系统标准题材。 */
    @GetMapping("/genres")
    public Result<List<GenreOption>> getStandardGenres(@RequestParam String contentType) {
        return Result.ok(tagService.getStandardGenres(contentType).stream()
                .map(GenreOption::from)
                .toList());
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
     * 按标签筛选内容
     */
    @GetMapping("/{tagId}/content")
    public Result<?> getContentByTag(
            @PathVariable Long tagId,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(tagService.getContentIdsByTag(tagId, contentType, page, size));
    }
}
