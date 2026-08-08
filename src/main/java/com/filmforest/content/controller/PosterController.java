package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.PosterContentRef;
import com.filmforest.content.dto.PosterResolveRequest;
import com.filmforest.content.dto.PosterResolutionView;
import com.filmforest.content.service.PosterEnrichmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/poster")
public class PosterController {

    private final PosterEnrichmentService service;

    public PosterController(PosterEnrichmentService service) {
        this.service = service;
    }

    @PostMapping("/enrich")
    public Result<PosterResolutionView> enrich(@Valid @RequestBody PosterContentRef body,
                                               HttpServletRequest request) {
        return Result.ok(service.enrich(userId(request), body.contentType(), body.contentId()));
    }

    @PostMapping("/resolve")
    public Result<List<PosterResolutionView>> resolve(@Valid @RequestBody PosterResolveRequest body,
                                                      HttpServletRequest request) {
        return Result.ok(service.resolve(userId(request), body.items()));
    }

    private long userId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalStateException("认证用户缺失");
        return userId;
    }
}
