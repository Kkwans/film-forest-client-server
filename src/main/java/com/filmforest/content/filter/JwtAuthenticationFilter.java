package com.filmforest.content.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.User;
import com.filmforest.content.service.UserService;
import com.filmforest.content.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JWT 认证过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_READ_PREFIXES = Set.of(
            "/api/movies",
            "/api/dramas",
            "/api/animes",
            "/api/varieties",
            "/api/short-dramas",
            "/api/search",
            "/api/resources",
            "/api/tags"
    );
    private static final Pattern RELATED_PATH = Pattern.compile(
            "^/api/(movie|drama|anime|variety|short_drama)/\\d+/related$"
    );

    private final JwtUtil jwtUtil;
    private final UserService userService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 放行公开接口
        if (isPublicRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, 401, "未登录，请先登录");
            return;
        }

        String token = authHeader.substring(7);
        final Claims claims;
        final Long userId;
        try {
            claims = jwtUtil.parseToken(token);
            userId = Long.parseLong(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ignored) {
            writeError(response, 401, "Token无效或已过期，请重新登录");
            return;
        }

        User user = userService.getById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || Integer.valueOf(1).equals(user.getDeleted())) {
            writeError(response, 401, "登录状态已失效，请重新登录");
            return;
        }

        request.setAttribute("userId", user.getId());
        request.setAttribute("username", user.getUsername());
        request.setAttribute("role", user.getRole());

        filterChain.doFilter(request, response);
    }

    boolean isPublicRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (path.equals("/api/auth/login") || path.equals("/api/health")) {
            return true;
        }
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        if (path.equals("/api/recommend") || RELATED_PATH.matcher(path).matches()) {
            return true;
        }
        return PUBLIC_READ_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }
}
