package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poster_enrichment_job")
public class PosterEnrichmentJob {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String status;
    private Integer cancelRequested;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contentType;
    private Integer totalCount;
    private Integer processedCount;
    private Integer matchedCount;
    private Integer pendingCount;
    private Integer failedCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentContentType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long currentContentId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorSummary;
    private LocalDateTime queuedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime heartbeatAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime finishedAt;
}
