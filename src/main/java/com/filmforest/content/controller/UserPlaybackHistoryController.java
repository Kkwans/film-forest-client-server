package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.UserPlaybackHistoryRequest;
import com.filmforest.content.dto.UserPlaybackHistoryView;
import com.filmforest.content.service.UserPlaybackHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户跨设备播放历史 API。 */
@RestController
@RequestMapping("/api/user")
public class UserPlaybackHistoryController {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final UserPlaybackHistoryService userPlaybackHistoryService;

    public UserPlaybackHistoryController(UserPlaybackHistoryService userPlaybackHistoryService) {
        this.userPlaybackHistoryService = userPlaybackHistoryService;
    }

    @GetMapping("/play-history")
    public Result<List<UserPlaybackHistoryView>> list(HttpServletRequest request,
                                                      @RequestParam(defaultValue = "20") int limit) {
        Long userId = authenticatedUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录，请先登录");
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            return Result.fail(400, "limit 必须在 1 到 100 之间");
        }
        return Result.ok(userPlaybackHistoryService.list(userId, limit));
    }

    @PutMapping("/play-history")
    public Result<?> upsert(HttpServletRequest request,
                            @Valid @RequestBody UserPlaybackHistoryRequest body) {
        Long userId = authenticatedUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录，请先登录");
        }
        userPlaybackHistoryService.upsert(userId, body);
        return Result.ok();
    }

    @DeleteMapping("/play-history/{contentType}/{contentId}")
    public Result<?> remove(HttpServletRequest request,
                            @PathVariable String contentType,
                            @PathVariable Long contentId) {
        Long userId = authenticatedUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录，请先登录");
        }
        userPlaybackHistoryService.remove(userId, contentType, contentId);
        return Result.ok();
    }

    @DeleteMapping("/play-history")
    public Result<?> clear(HttpServletRequest request) {
        Long userId = authenticatedUserId(request);
        if (userId == null) {
            return Result.fail(401, "未登录，请先登录");
        }
        userPlaybackHistoryService.clear(userId);
        return Result.ok();
    }

    private Long authenticatedUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId instanceof Number ? ((Number) userId).longValue() : null;
    }
}
