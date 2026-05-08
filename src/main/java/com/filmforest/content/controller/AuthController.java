package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.User;
import com.filmforest.content.service.UserService;
import com.filmforest.content.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器：注册/登录/获取当前用户
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        String email = params.get("email");

        if (username == null || username.isBlank()) {
            return Result.fail(400, "用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            return Result.fail(400, "密码长度不能少于6位");
        }

        try {
            User user = userService.register(username, password, email);
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("user", sanitizeUser(user));
            return Result.ok(data);
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || username.isBlank()) {
            return Result.fail(400, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            return Result.fail(400, "密码不能为空");
        }

        try {
            User user = userService.login(username, password);
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("user", sanitizeUser(user));
            return Result.ok(data);
        } catch (RuntimeException e) {
            return Result.fail(400, e.getMessage());
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
        map.put("createdAt", user.getCreatedAt());
        return map;
    }
}
