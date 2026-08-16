package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.MovieSeriesItemVO;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.model.ContentStatus;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.ContentDetailEnrichmentService;
import com.filmforest.content.service.MovieService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;
    private final ContentDetailEnrichmentService detailEnrichmentService;

    public MovieController(MovieService movieService, ContentDetailEnrichmentService detailEnrichmentService) {
        this.movieService = movieService;
        this.detailEnrichmentService = detailEnrichmentService;
    }

    /**
     * 电影列表（分页 + 筛选 + 排序）
     */
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
        return Result.ok(movieService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo,
                tag, hasResource, sortDir, language));
    }

    /**
     * 电影详情
     */
    @GetMapping("/{id}")
    public Result<Movie> detail(@PathVariable Long id) {
        Movie movie = movieService.getDetail(id);
        return movie != null ? Result.ok(detailEnrichmentService.enrich(ContentType.MOVIE, movie)) : Result.fail("电影不存在");
    }

    /**
     * 当前电影所属系列，按系列序号返回，用于详情页快速切换同系列影片。
     */
    @GetMapping("/{id}/series")
    public Result<List<MovieSeriesItemVO>> series(@PathVariable Long id) {
        Movie current = movieService.getDetail(id);
        if (current == null || current.getSeriesName() == null || current.getSeriesName().isBlank()) {
            return Result.ok(List.of());
        }

        List<MovieSeriesItemVO> items = movieService.list(new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getSeriesName, current.getSeriesName())
                        .eq(Movie::getStatus, ContentStatus.PUBLISHED.code())
                        .orderByAsc(Movie::getSeriesOrder)
                        .orderByAsc(Movie::getId))
                .stream()
                .map(movie -> new MovieSeriesItemVO(movie.getId(), movie.getTitle(), movie.getYear(), movie.getSeriesOrder()))
                .toList();
        return Result.ok(items);
    }

}
