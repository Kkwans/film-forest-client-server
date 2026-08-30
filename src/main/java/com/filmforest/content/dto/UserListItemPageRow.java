package com.filmforest.content.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户片单分页的数据库投影行。
 *
 * <p>时间字段保留为数据库的 UTC {@link LocalDateTime}，由服务层统一转换为
 * API 使用的 {@code OffsetDateTime}，避免 JDBC 会话时区影响公开响应。</p>
 */
@Data
public class UserListItemPageRow {

    private Long id;
    private Long listId;
    private Long movieId;
    private String contentType;
    private LocalDateTime addedAt;
    private LocalDateTime watchedAt;

    private String title;
    private String cover;
    private String alias;
    private Integer year;
    private BigDecimal rating;
    private Integer scoreDoubanCount;
    private Integer scoreImdbCount;
    private Integer scoreRtCriticCount;
    private Integer scoreRtAudienceCount;
    private String region;
    private String genre;
    private String director;
    private String writer;
    private String actor;
    private String releaseDate;
    private Integer duration;
    private Integer totalEpisode;

    private BigDecimal userRating;
    private String note;
}
