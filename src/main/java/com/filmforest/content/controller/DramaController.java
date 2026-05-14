package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.service.DramaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 电视剧 API 控制器
 * 提供电视剧列表查询和详情获取接口
 */
@RestController
@RequestMapping("/api/dramas")
public class DramaController {

    @Autowired
    private DramaService dramaService;

    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        return Result.ok(dramaService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo, sortDir));
    }

    @GetMapping("/{id}")
    public Result<Drama> detail(@PathVariable Long id) {
        Drama drama = dramaService.getDetail(id);
        return drama != null ? Result.ok(drama) : Result.fail("剧集不存在");
    }

    @PostMapping
    public Result<?> add(@RequestBody Drama drama) {
        dramaService.save(drama);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Drama drama) {
        drama.setId(id);
        dramaService.updateById(drama);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        dramaService.removeById(id);
        return Result.ok();
    }
}
