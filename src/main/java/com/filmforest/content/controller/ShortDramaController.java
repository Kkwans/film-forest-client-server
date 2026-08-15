package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.ContentDetailEnrichmentService;
import com.filmforest.content.service.ShortDramaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/short-dramas")
public class ShortDramaController {

    private final ShortDramaService shortDramaService;
    private final ContentDetailEnrichmentService detailEnrichmentService;

    public ShortDramaController(ShortDramaService shortDramaService, ContentDetailEnrichmentService detailEnrichmentService) {
        this.shortDramaService = shortDramaService;
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
        return Result.ok(shortDramaService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo,
                tag, hasResource, sortDir, language));
    }

    @GetMapping("/{id}")
    public Result<ShortDrama> detail(@PathVariable Long id) {
        ShortDrama shortDrama = shortDramaService.getDetail(id);
        return shortDrama != null ? Result.ok(detailEnrichmentService.enrich(ContentType.SHORT_DRAMA, shortDrama)) : Result.fail("短剧不存在");
    }

}
