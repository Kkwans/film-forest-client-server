package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.ContentDetailEnrichmentService;
import com.filmforest.content.service.AnimeService;
import org.springframework.web.bind.annotation.*;

/**
 * 动漫 API 控制器
 * 提供动漫列表查询和详情获取接口
 */
@RestController
@RequestMapping("/api/animes")
public class AnimeController {

    private final AnimeService animeService;
    private final ContentDetailEnrichmentService detailEnrichmentService;

    public AnimeController(AnimeService animeService, ContentDetailEnrichmentService detailEnrichmentService) {
        this.animeService = animeService;
        this.detailEnrichmentService = detailEnrichmentService;
    }

    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) Long tag,
            @RequestParam(required = false) Boolean hasResource,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        return Result.ok(animeService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo,
                tag, hasResource, sortDir, language));
    }

    @GetMapping("/{id}")
    public Result<Anime> detail(@PathVariable Long id) {
        Anime anime = animeService.getDetail(id);
        return anime != null ? Result.ok(detailEnrichmentService.enrich(ContentType.ANIME, anime)) : Result.fail("动漫不存在");
    }

}
