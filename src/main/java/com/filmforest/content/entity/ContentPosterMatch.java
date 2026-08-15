package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("content_poster_match")
public class ContentPosterMatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String contentType;
    private Long contentId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourcePosterUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tmdbMediaType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long tmdbId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal tmdbScore;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer tmdbVoteCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String posterPath;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String posterLanguage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal confidence;
    private String matchStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String diagnostic;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime matchedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
