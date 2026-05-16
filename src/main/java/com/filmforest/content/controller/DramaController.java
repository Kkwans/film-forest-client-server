package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.service.DramaService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
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
    public Result<?> add(@Valid @RequestBody Drama drama) {
        log.info("[Drama] 创建剧集: id={}, title={}", drama.getId(), drama.getTitle());
        dramaService.save(drama);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody Drama drama) {
        log.info("[Drama] 更新剧集: id={}, title={}", id, drama.getTitle());
        drama.setId(id);
        dramaService.updateById(drama);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        log.info("[Drama] 删除剧集: id={}", id);
        dramaService.removeById(id);
        return Result.ok();
    }
}
