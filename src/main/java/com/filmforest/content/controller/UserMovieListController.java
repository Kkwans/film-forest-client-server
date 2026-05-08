package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.UserListItemVO;
import com.filmforest.content.entity.UserMovieList;
import com.filmforest.content.service.UserMovieListService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户片单控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserMovieListController {

    @Autowired
    private UserMovieListService userMovieListService;

    /**
     * 获取当前用户所有片单
     */
    @GetMapping("/lists")
    public Result<?> getLists(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<UserMovieList> lists = userMovieListService.getUserLists(userId);
        return Result.ok(lists);
    }

    /**
     * 创建自定义片单
     */
    @PostMapping("/lists")
    public Result<?> createList(HttpServletRequest request, @RequestBody Map<String, String> params) {
        Long userId = (Long) request.getAttribute("userId");
        String name = params.get("name");
        String description = params.get("description");

        if (name == null || name.isBlank()) {
            return Result.fail(400, "片单名称不能为空");
        }

        try {
            UserMovieList list = userMovieListService.createList(userId, name, description);
            return Result.ok(list);
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 编辑片单
     */
    @PutMapping("/lists/{id}")
    public Result<?> updateList(HttpServletRequest request, @PathVariable Long id,
                                @RequestBody Map<String, String> params) {
        Long userId = (Long) request.getAttribute("userId");
        String name = params.get("name");
        String description = params.get("description");

        try {
            userMovieListService.updateList(userId, id, name, description);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 删除片单（仅自定义）
     */
    @DeleteMapping("/lists/{id}")
    public Result<?> deleteList(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            userMovieListService.deleteList(userId, id);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 添加影视到片单
     */
    @PostMapping("/lists/{id}/items")
    public Result<?> addItem(HttpServletRequest request, @PathVariable Long id,
                             @RequestBody Map<String, Object> params) {
        Long userId = (Long) request.getAttribute("userId");
        Long movieId = params.get("movieId") != null ? Long.valueOf(params.get("movieId").toString()) : null;
        String contentType = (String) params.get("contentType");

        if (movieId == null || contentType == null || contentType.isBlank()) {
            return Result.fail(400, "movieId 和 contentType 不能为空");
        }

        // 可选参数：评分和备注
        BigDecimal rating = null;
        if (params.get("rating") != null) {
            rating = new BigDecimal(params.get("rating").toString());
        }
        String note = (String) params.get("note");

        try {
            userMovieListService.addItem(userId, id, movieId, contentType, rating, note);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 从片单移除影视
     */
    @DeleteMapping("/lists/{id}/items")
    public Result<?> removeItem(HttpServletRequest request, @PathVariable Long id,
                                @RequestBody Map<String, Object> params) {
        Long userId = (Long) request.getAttribute("userId");
        Long movieId = params.get("movieId") != null ? Long.valueOf(params.get("movieId").toString()) : null;
        String contentType = (String) params.get("contentType");

        if (movieId == null || contentType == null || contentType.isBlank()) {
            return Result.fail(400, "movieId 和 contentType 不能为空");
        }

        try {
            userMovieListService.removeItem(userId, id, movieId, contentType);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 获取片单内容（分页）
     */
    @GetMapping("/lists/{id}/items")
    public Result<?> getListItems(HttpServletRequest request, @PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            IPage<UserListItemVO> items = userMovieListService.getListItems(userId, id, page, size);
            return Result.ok(items);
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 查询影视在哪些片单中
     */
    @GetMapping("/movie-status")
    public Result<?> getMovieStatus(HttpServletRequest request,
                                    @RequestParam Long movieId,
                                    @RequestParam String contentType) {
        Long userId = (Long) request.getAttribute("userId");
        List<Map<String, Object>> status = userMovieListService.getMovieStatus(userId, movieId, contentType);
        return Result.ok(status);
    }
}
