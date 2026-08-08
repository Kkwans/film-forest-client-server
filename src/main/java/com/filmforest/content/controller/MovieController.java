package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    @Autowired
    private MovieService movieService;

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
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) Long tag,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        return Result.ok(movieService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo, tag, sortDir));
    }

    /**
     * 电影详情
     */
    @GetMapping("/{id}")
    public Result<Movie> detail(@PathVariable Long id) {
        Movie movie = movieService.getDetail(id);
        return movie != null ? Result.ok(movie) : Result.fail("电影不存在");
    }

}
