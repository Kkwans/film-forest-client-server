package com.filmforest.content.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 片单明细 VO（包含影视基本信息）
 */
@Data
public class UserListItemVO {

    private Long id;                // 片单明细ID
    private Long listId;            // 片单ID
    private Long movieId;           // 影视ID
    private String contentType;     // 内容类型：movie/drama/variety/anime/short_drama
    private LocalDateTime addedAt;

    // 影视基本信息（从对应表查询填充）
    private String title;
    private String cover;
    private Integer year;
    private BigDecimal rating;           // 豆瓣评分（影视评分）
    private String region;              // 地区（JSON数组）
    private String genre;               // 类型（JSON数组）
    private String director;            // 导演（JSON数组）
    private String actor;               // 主演（JSON数组）
    private Integer duration;           // 时长（分钟）
    private Integer totalEpisode;       // 总集数

    // 用户标记信息
    private BigDecimal userRating;      // 用户评分（10分制）
    private String note;                // 用户备注/感受
}
