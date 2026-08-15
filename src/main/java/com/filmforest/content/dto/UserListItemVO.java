package com.filmforest.content.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 片单明细 VO（包含影视基本信息）
 */
@Data
public class UserListItemVO {

    private Long id;                // 片单明细ID
    private Long listId;            // 片单ID
    private Long movieId;           // 影视ID
    private String contentType;     // 内容类型：movie/drama/variety/anime/short_drama
    /** API 输出带 UTC offset 的 ISO-8601 时间；数据库仍使用 UTC LocalDateTime。 */
    private OffsetDateTime addedAt;
    private OffsetDateTime watchedAt;

    // 影视基本信息（从对应表查询填充）
    private String title;
    private String cover;
    private String alias;
    private Integer year;
    private BigDecimal rating;           // 豆瓣评分（影视评分）
    private Integer scoreDoubanCount;
    private Integer scoreImdbCount;
    private Integer scoreRtCriticCount;
    private Integer scoreRtAudienceCount;
    private String region;              // 地区（JSON数组）
    private String genre;               // 类型（JSON数组）
    private String director;            // 导演（JSON数组）
    private String writer;              // 编剧（JSON数组）
    private String actor;               // 主演（JSON数组）
    private String releaseDate;         // 上映/首播日期
    private Integer duration;           // 时长（分钟）
    private Integer totalEpisode;       // 总集数

    // 用户标记信息
    private BigDecimal userRating;      // 用户评分（10分制）
    private String note;                // 用户备注/感受
}
