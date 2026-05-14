package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.service.ShortDramaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 短剧 API 控制器
 * 提供短剧列表查询和详情获取接口
 */
@RestController
@RequestMapping("/api/short-dramas")
public class ShortDramaController {

    @Autowired
    private ShortDramaService shortDramaService;

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
        return Result.ok(shortDramaService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo, sortDir));
    }

    @GetMapping("/{id}")
    public Result<ShortDrama> detail(@PathVariable Long id) {
        ShortDrama shortDrama = shortDramaService.getDetail(id);
        return shortDrama != null ? Result.ok(shortDrama) : Result.fail("短剧不存在");
    }

    @PostMapping
    public Result<?> add(@RequestBody ShortDrama shortDrama) {
        shortDramaService.save(shortDrama);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody ShortDrama shortDrama) {
        shortDrama.setId(id);
        shortDramaService.updateById(shortDrama);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        shortDramaService.removeById(id);
        return Result.ok();
    }
}
