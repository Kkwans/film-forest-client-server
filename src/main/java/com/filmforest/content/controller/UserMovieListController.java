package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.UserListItemVO;
import com.filmforest.content.dto.ContentStatusQuery;
import com.filmforest.content.entity.UserMovieList;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.UserMovieListService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户片单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserMovieListController {

    private final UserMovieListService userMovieListService;

    public UserMovieListController(UserMovieListService userMovieListService) {
        this.userMovieListService = userMovieListService;
    }

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
            log.warn("[UserList] 创建片单失败: 名称为空, userId={}", userId);
            return Result.fail(400, "片单名称不能为空");
        }

        try {
            UserMovieList list = userMovieListService.createList(userId, name, description);
            log.info("[UserList] 创建片单成功: userId={}, listId={}, name={}", userId, list.getId(), name);
            return Result.ok(list);
        } catch (RuntimeException e) {
            log.warn("[UserList] 创建片单失败: userId={}, reason={}", userId, e.getMessage());
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
            log.info("[UserList] 更新片单: userId={}, listId={}", userId, id);
            return Result.ok();
        } catch (RuntimeException e) {
            log.warn("[UserList] 更新片单失败: userId={}, listId={}, reason={}", userId, id, e.getMessage());
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
            log.info("[UserList] 删除片单: userId={}, listId={}", userId, id);
            return Result.ok();
        } catch (RuntimeException e) {
            log.warn("[UserList] 删除片单失败: userId={}, listId={}, reason={}", userId, id, e.getMessage());
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
        Long movieId = parseId(params.get("movieId"));
        String contentType = parseString(params.get("contentType"));

        if (movieId == null || contentType == null || contentType.isBlank()) {
            log.warn("[UserList] 添加影视失败: 参数不完整, userId={}, listId={}", userId, id);
            return Result.fail(400, "movieId 和 contentType 不能为空");
        }

        try {
            // 可选参数：评分和备注；格式错误也通过既有 400 响应返回。
            BigDecimal rating = parseRating(params.get("rating"));
            String note = parseNote(params.get("note"));
            userMovieListService.addItem(userId, id, movieId, contentType, rating, note);
            log.info("[UserList] 添加影视到片单: userId={}, listId={}, movieId={}, contentType={}", userId, id, movieId, contentType);
            return Result.ok();
        } catch (RuntimeException e) {
            log.warn("[UserList] 添加影视失败: userId={}, listId={}, reason={}", userId, id, e.getMessage());
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
        Long movieId = parseId(params.get("movieId"));
        String contentType = parseString(params.get("contentType"));

        if (movieId == null || contentType == null || contentType.isBlank()) {
            log.warn("[UserList] 移除影视失败: 参数不完整, userId={}, listId={}", userId, id);
            return Result.fail(400, "movieId 和 contentType 不能为空");
        }

        try {
            userMovieListService.removeItem(userId, id, movieId, contentType);
            log.info("[UserList] 从片单移除影视: userId={}, listId={}, movieId={}, contentType={}", userId, id, movieId, contentType);
            return Result.ok();
        } catch (RuntimeException e) {
            log.warn("[UserList] 移除影视失败: userId={}, listId={}, reason={}", userId, id, e.getMessage());
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 获取片单内容（分页）
     */
    @GetMapping("/lists/{id}/items")
    public Result<?> getListItems(HttpServletRequest request, @PathVariable Long id,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(defaultValue = "addedAt") String sort,
                                  @RequestParam(defaultValue = "desc") String sortDir,
                                  @RequestParam(required = false) String contentType) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            String normalizedType = contentType == null || contentType.isBlank()
                    ? null
                    : ContentType.parse(contentType).code();
            int safePage = Math.max(page, 1);
            int safeSize = Math.min(Math.max(size, 1), 100);
            IPage<UserListItemVO> items = userMovieListService.getListItems(
                    userId, id, safePage, safeSize, sort, sortDir, normalizedType);
            log.debug("[UserList] 获取片单内容: userId={}, listId={}, page={}, size={}", userId, id, page, size);
            return Result.ok(items);
        } catch (RuntimeException e) {
            log.warn("[UserList] 获取片单内容失败: userId={}, listId={}, reason={}", userId, id, e.getMessage());
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 更新片单条目（评分/备注）
     */
    @PutMapping("/lists/{id}/items")
    public Result<?> updateItem(HttpServletRequest request, @PathVariable Long id,
                                @RequestBody Map<String, Object> params) {
        Long userId = (Long) request.getAttribute("userId");
        Long movieId = parseId(params.get("movieId"));
        String contentType = parseString(params.get("contentType"));

        if (movieId == null || contentType == null || contentType.isBlank()) {
            log.warn("[UserList] 更新条目失败: 参数不完整, userId={}, listId={}", userId, id);
            return Result.fail(400, "movieId 和 contentType 不能为空");
        }

        try {
            BigDecimal rating = parseRating(params.get("rating"));
            String note = parseNote(params.get("note"));
            userMovieListService.updateItem(userId, id, movieId, contentType, rating, note);
            log.info("[UserList] 更新片单条目: userId={}, listId={}, movieId={}, contentType={}", userId, id, movieId, contentType);
            return Result.ok();
        } catch (RuntimeException e) {
            log.warn("[UserList] 更新条目失败: userId={}, listId={}, reason={}", userId, id, e.getMessage());
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 批量从片单移除影视
     */
    @DeleteMapping("/lists/{id}/items/batch")
    public Result<?> batchRemoveItems(HttpServletRequest request, @PathVariable Long id,
                                      @RequestBody Map<String, Object> params) {
        Long userId = (Long) request.getAttribute("userId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) params.get("items");

        if (items == null || items.isEmpty()) {
            log.warn("[UserList] 批量移除失败: items 为空, userId={}, listId={}", userId, id);
            return Result.fail(400, "items 不能为空");
        }

        try {
            userMovieListService.batchRemoveItems(userId, id, items);
            log.info("[UserList] 批量移除影视: userId={}, listId={}, count={}", userId, id, items.size());
            return Result.ok();
        } catch (RuntimeException e) {
            log.warn("[UserList] 批量移除失败: userId={}, listId={}, reason={}", userId, id, e.getMessage());
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
        log.debug("[UserList] 查询影视状态: userId={}, movieId={}, contentType={}, resultCount={}", userId, movieId, contentType, status.size());
        return Result.ok(status);
    }

    /**
     * 批量查询影视在哪些片单中
     */
    @GetMapping("/movie-status-batch")
    public Result<?> getMovieStatusBatch(HttpServletRequest request,
                                         @RequestParam List<Long> movieIds,
                                         @RequestParam String contentType) {
        Long userId = (Long) request.getAttribute("userId");
        Map<Long, List<Map<String, Object>>> result = userMovieListService.getMovieStatusBatch(userId, movieIds, contentType);
        log.debug("[UserList] 批量查询影视状态: userId={}, movieIds={}, contentType={}, resultCount={}", userId, movieIds.size(), contentType, result.size());
        return Result.ok(result);
    }

    /**
     * 批量查询异构内容状态。请求和响应都以 contentType + contentId 唯一定位，
     * 避免不同内容表中相同数字 ID 相互覆盖。
     */
    @PostMapping("/movie-status-batch")
    public Result<?> getMixedContentStatusBatch(
            HttpServletRequest request,
            @Valid @RequestBody List<@Valid ContentStatusQuery> queries) {
        Long userId = (Long) request.getAttribute("userId");
        var result = userMovieListService.getContentStatusBatch(userId, queries);
        log.debug("[UserList] 混合批量状态: userId={}, queryCount={}, resultCount={}",
                userId, queries.size(), result.size());
        return Result.ok(result);
    }

    private Long parseId(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.toString());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseString(Object raw) {
        return raw instanceof String ? (String) raw : null;
    }

    private BigDecimal parseRating(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("rating 格式无效");
        }
    }

    private String parseNote(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException("note 格式无效");
        }
        return (String) raw;
    }
}
