package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.PageResult;
import com.filmforest.content.dto.PosterEnrichmentJobRequest;
import com.filmforest.content.dto.PosterEnrichmentJobView;
import com.filmforest.content.service.PosterEnrichmentJobService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/poster/enrichment-jobs")
public class PosterEnrichmentJobController {

    private final PosterEnrichmentJobService service;

    public PosterEnrichmentJobController(PosterEnrichmentJobService service) {
        this.service = service;
    }

    @PostMapping
    public Result<PosterEnrichmentJobView> start(@RequestBody(required = false) PosterEnrichmentJobRequest body,
                                                HttpServletRequest request) {
        return Result.ok(service.start(userId(request), body == null ? null : body.contentType()));
    }

    @GetMapping("/latest")
    public Result<PosterEnrichmentJobView> latest(HttpServletRequest request) {
        return Result.ok(service.latest(userId(request)));
    }

    @GetMapping
    public Result<PageResult<PosterEnrichmentJobView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        return Result.ok(service.list(userId(request), page, size));
    }

    @GetMapping("/{id}")
    public Result<PosterEnrichmentJobView> get(@PathVariable long id, HttpServletRequest request) {
        return Result.ok(service.get(userId(request), id));
    }

    @PostMapping("/{id}/cancel")
    public Result<PosterEnrichmentJobView> cancel(@PathVariable long id, HttpServletRequest request) {
        return Result.ok(service.cancel(userId(request), id));
    }

    private long userId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalStateException("认证用户缺失");
        return userId;
    }
}
