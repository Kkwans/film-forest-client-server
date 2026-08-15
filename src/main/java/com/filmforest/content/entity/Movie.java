package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电影表
 */
@Data
@TableName("movie")
public class Movie {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "电影标题不能为空")
    private String title;                    // 标题
    private String alias;                   // 别名（JSON数组）
    private String posterUrl;                // 海报URL
    private Integer year;                   // 年份
    private String director;                // 导演（JSON数组）
    private String writer;                  // 编剧中（JSON数组）
    private String actor;                   // 演员（JSON数组）
    private String genre;                   // 类型（JSON数组）
    private String region;                  // 地区（JSON数组）
    private String language;                // 语言（JSON数组）
    private String releaseDate;            // 上映日期
    private Integer duration;               // 时长（分钟）
    private String storyline;              // 剧情简介
    private BigDecimal scoreDouban;        // 豆瓣评分
    private Integer scoreDoubanCount;      // 豆瓣评分人数
    private BigDecimal scoreImdb;          // IMDb评分
    private Integer scoreImdbCount;        // IMDb评分人数
    private BigDecimal scoreRt;            // 烂番茄评分(%)
    private Integer scoreRtCriticCount;    // 烂番茄影评人评价数
    private Integer scoreRtAudienceCount;  // 烂番茄观众评价数
    @TableField(exist = false)
    private BigDecimal tmdbScore;          // TMDB 社区评分（匹配表投影）
    @TableField(exist = false)
    private Integer tmdbVoteCount;         // TMDB 社区投票人数（匹配表投影）
    private String seriesName;             // 系列名称
    private Integer seriesOrder;           // 系列序号
    private Integer status;                 // 状态：0=未上线 1=已上线（默认1）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;                 // 逻辑删除：0=未删除 1=已删除
}
