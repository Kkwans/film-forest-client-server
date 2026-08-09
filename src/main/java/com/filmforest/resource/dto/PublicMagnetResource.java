package com.filmforest.resource.dto;

import com.filmforest.resource.entity.ResourceMagnet;

import java.time.LocalDateTime;
import java.util.Locale;

/** 用户端磁力资源稳定投影，并给出互斥的清晰度/字幕分类。 */
public record PublicMagnetResource(
        Long id,
        String title,
        String magnetUrl,
        String resolution,
        boolean hasSubtitle,
        boolean specialSubtitle,
        String qualityCategory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PublicMagnetResource from(ResourceMagnet resource) {
        boolean special = Boolean.TRUE.equals(resource.getIsSpecialSub())
                || contains(resource.getTitle(), "特效");
        boolean subtitle = special
                || Boolean.TRUE.equals(resource.getHasSubtitle())
                || hasSubtitleMarker(resource.getTitle());
        String resolution = normalizeResolution(resource.getResolution(), resource.getTitle());
        return new PublicMagnetResource(
                resource.getId(), resource.getTitle(), resource.getMagnetUrl(), resolution,
                subtitle, special, category(resolution, subtitle, special),
                resource.getCreatedAt(), resource.getUpdatedAt());
    }

    static String category(String resolution, boolean subtitle, boolean special) {
        if ("未知".equals(resolution)) return "未知";
        if (special) return "特效" + resolution;
        if (subtitle) return "中字" + resolution;
        return resolution;
    }

    static String normalizeResolution(String value, String title) {
        String normalized = ((value == null ? "" : value) + " " + (title == null ? "" : title))
                .toUpperCase(Locale.ROOT);
        if (normalized.contains("4K") || normalized.contains("2160")) return "4K";
        if (normalized.contains("1080")) return "1080p";
        if (normalized.contains("720")) return "720p";
        if (normalized.contains("480")) return "480p";
        return "未知";
    }

    private static boolean hasSubtitleMarker(String title) {
        if (title == null) return false;
        String upper = title.toUpperCase(Locale.ROOT);
        return title.contains("中字")
                || title.contains("字幕")
                || title.contains("双字")
                || title.contains("简繁")
                || upper.contains("CHS")
                || upper.contains("CHT")
                || upper.contains("SUB");
    }

    private static boolean contains(String value, String marker) {
        return value != null && value.contains(marker);
    }
}
