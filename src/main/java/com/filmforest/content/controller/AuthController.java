package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.dto.LoginRequest;
import com.filmforest.content.entity.User;
import com.filmforest.content.service.LoginAttemptService;
import com.filmforest.content.service.UserService;
import com.filmforest.content.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器：登录/获取当前用户
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    public AuthController(UserService userService, JwtUtil jwtUtil, LoginAttemptService loginAttemptService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String remoteAddress = servletRequest.getRemoteAddr();
        if (loginAttemptService.isBlocked(remoteAddress, request.getUsername())) {
            return Result.fail(429, "登录尝试过多，请稍后再试");
        }
        log.info("登录请求: username={}", request.getUsername());
        try {
            User user = userService.login(request.getUsername(), request.getPassword());
            loginAttemptService.recordSuccess(remoteAddress, request.getUsername());
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("user", sanitizeUser(user));
            log.info("登录成功: userId={}", user.getId());
            return Result.ok(data);
        } catch (BusinessException e) {
            loginAttemptService.recordFailure(remoteAddress, request.getUsername());
            log.warn("登录失败: username={}, reason={}", request.getUsername(), e.getMessage());
            return Result.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<?> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "未登录");
        }

        User user = userService.getById(userId);
        if (user == null) {
            log.warn("用户不存在: userId={}", userId);
            return Result.fail(404, "用户不存在");
        }

        return Result.ok(sanitizeUser(user));
    }

    /**
     * 脱敏返回用户信息（去掉密码哈希）
     */
    private Map<String, Object> sanitizeUser(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("avatarUrl", user.getAvatarUrl());
        map.put("status", user.getStatus());
        map.put("role", user.getRole());
        map.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
        map.put("createdAt", user.getCreatedAt());
        return map;
    }
}
