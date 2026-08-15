package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.ContentDetailEnrichmentService;
import com.filmforest.content.service.VarietyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/varieties")
public class VarietyController {

    private final VarietyService varietyService;
    private final ContentDetailEnrichmentService detailEnrichmentService;

    public VarietyController(VarietyService varietyService, ContentDetailEnrichmentService detailEnrichmentService) {
        this.varietyService = varietyService;
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
        return Result.ok(varietyService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo,
                tag, hasResource, sortDir, language));
    }

    @GetMapping("/{id}")
    public Result<Variety> detail(@PathVariable Long id) {
        Variety variety = varietyService.getDetail(id);
        return variety != null ? Result.ok(detailEnrichmentService.enrich(ContentType.VARIETY, variety)) : Result.fail("综艺不存在");
    }

}
