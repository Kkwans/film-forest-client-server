package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.ContentDetailEnrichmentService;
import com.filmforest.content.service.DramaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dramas")
public class DramaController {

    private final DramaService dramaService;
    private final ContentDetailEnrichmentService detailEnrichmentService;

    public DramaController(DramaService dramaService, ContentDetailEnrichmentService detailEnrichmentService) {
        this.dramaService = dramaService;
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
        return Result.ok(dramaService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo,
                tag, hasResource, sortDir, language));
    }

    @GetMapping("/{id}")
    public Result<Drama> detail(@PathVariable Long id) {
        Drama drama = dramaService.getDetail(id);
        return drama != null ? Result.ok(detailEnrichmentService.enrich(ContentType.DRAMA, drama)) : Result.fail("剧集不存在");
    }

}
