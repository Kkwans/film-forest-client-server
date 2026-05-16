package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.service.ShortDramaService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
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
    public Result<?> add(@Valid @RequestBody ShortDrama shortDrama) {
        log.info("[ShortDrama] 创建短剧: id={}, title={}", shortDrama.getId(), shortDrama.getTitle());
        shortDramaService.save(shortDrama);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody ShortDrama shortDrama) {
        log.info("[ShortDrama] 更新短剧: id={}, title={}", id, shortDrama.getTitle());
        shortDrama.setId(id);
        shortDramaService.updateById(shortDrama);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        log.info("[ShortDrama] 删除短剧: id={}", id);
        shortDramaService.removeById(id);
        return Result.ok();
    }
}
