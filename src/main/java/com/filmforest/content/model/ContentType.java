package com.filmforest.content.model;

import com.filmforest.common.exception.BusinessException;

import java.util.Arrays;
import java.util.Locale;

/**
 * 五类公开内容的唯一类型定义。
 *
 * <p>数据库与 API 统一使用 {@link #code}；页面路由和集合 API 仅作为展示层映射，
 * 避免各控制器自行维护 short/short_drama 等别名。</p>
 */
public enum ContentType {
    MOVIE("movie", "movie", "/api/movies", "电影"),
    DRAMA("drama", "drama", "/api/dramas", "剧集"),
    VARIETY("variety", "variety", "/api/varieties", "综艺"),
    ANIME("anime", "anime", "/api/animes", "动漫"),
    SHORT_DRAMA("short_drama", "short", "/api/short-dramas", "短剧");

    private final String code;
    private final String routeSegment;
    private final String collectionPath;
    private final String displayName;

    ContentType(String code, String routeSegment, String collectionPath, String displayName) {
        this.code = code;
        this.routeSegment = routeSegment;
        this.collectionPath = collectionPath;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String routeSegment() {
        return routeSegment;
    }

    public String collectionPath() {
        return collectionPath;
    }

    public String displayName() {
        return displayName;
    }

    public static ContentType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("内容类型不能为空");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("short".equals(normalized)) {
            normalized = SHORT_DRAMA.code;
        }
        final String candidate = normalized;
        return Arrays.stream(values())
                .filter(type -> type.code.equals(candidate))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的内容类型: " + raw));
    }
}
