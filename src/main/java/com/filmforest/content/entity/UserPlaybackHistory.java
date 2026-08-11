package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户跨设备播放历史。
 *
 * <p>内容标题、海报等展示字段不落库，读取时始终从当前公开内容表派生，避免
 * 客户端提交的文本成为可信来源。</p>
 */
@Data
@TableName("user_playback_history")
public class UserPlaybackHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String contentType;
    private Long contentId;
    private Long resourceOnlineId;
    private Integer episodeNumber;
    private String episodeTitle;
    private String sourceName;
    private String playbackType;
    private Long positionSeconds;
    private Long durationSeconds;
    private Boolean completed;
    private LocalDateTime lastPlayedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
