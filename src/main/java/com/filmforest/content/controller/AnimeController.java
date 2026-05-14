package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.service.AnimeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 动漫 API 控制器
 * 提供动漫列表查询和详情获取接口
 */
@RestController
@RequestMapping("/api/animes")
public class AnimeController {

    @Autowired
    private AnimeService animeService;

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
        return Result.ok(animeService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo, sortDir));
    }

    @GetMapping("/{id}")
    public Result<Anime> detail(@PathVariable Long id) {
        Anime anime = animeService.getDetail(id);
        return anime != null ? Result.ok(anime) : Result.fail("动漫不存在");
    }

    @PostMapping
    public Result<?> add(@Valid @RequestBody Anime anime) {
        animeService.save(anime);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody Anime anime) {
        anime.setId(id);
        animeService.updateById(anime);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        animeService.removeById(id);
        return Result.ok();
    }
}
