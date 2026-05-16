package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.service.VarietyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/varieties")
public class VarietyController {

    @Autowired
    private VarietyService varietyService;

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
        return Result.ok(varietyService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo, sortDir));
    }

    @GetMapping("/{id}")
    public Result<Variety> detail(@PathVariable Long id) {
        Variety variety = varietyService.getDetail(id);
        return variety != null ? Result.ok(variety) : Result.fail("综艺不存在");
    }

    @PostMapping
    public Result<?> add(@Valid @RequestBody Variety variety) {
        log.info("[Variety] 创建综艺: id={}, title={}", variety.getId(), variety.getTitle());
        varietyService.save(variety);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody Variety variety) {
        log.info("[Variety] 更新综艺: id={}, title={}", id, variety.getTitle());
        variety.setId(id);
        varietyService.updateById(variety);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        log.info("[Variety] 删除综艺: id={}", id);
        varietyService.removeById(id);
        return Result.ok();
    }
}
