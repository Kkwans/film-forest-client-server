package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.service.MovieService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
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
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        return Result.ok(movieService.pageList(page, size, year, region, genre, sort, yearFrom, yearTo, sortDir));
    }

    /**
     * 电影详情
     */
    @GetMapping("/{id}")
    public Result<Movie> detail(@PathVariable Long id) {
        Movie movie = movieService.getDetail(id);
        return movie != null ? Result.ok(movie) : Result.fail("电影不存在");
    }

    /**
     * 新增电影（管理端）
     */
    @PostMapping
    public Result<?> add(@Valid @RequestBody Movie movie) {
        log.info("[Movie] 创建电影: id={}, title={}", movie.getId(), movie.getTitle());
        movieService.save(movie);
        return Result.ok();
    }

    /**
     * 更新电影（管理端）
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody Movie movie) {
        log.info("[Movie] 更新电影: id={}, title={}", id, movie.getTitle());
        movie.setId(id);
        movieService.updateById(movie);
        return Result.ok();
    }

    /**
     * 删除电影（管理端）
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        log.info("[Movie] 删除电影: id={}", id);
        movieService.removeById(id);
        return Result.ok();
    }
}
