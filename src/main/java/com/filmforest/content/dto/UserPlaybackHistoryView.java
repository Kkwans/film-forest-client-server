package com.filmforest.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户播放历史公开投影，仅返回可安全展示的来源元数据。 */
@Data
public class UserPlaybackHistoryView {

    private Long id;
    private String contentType;
    private Long contentId;
    private Long resourceId;
    private Integer episodeNumber;
    private String episodeTitle;
    private String sourceName;
    private String playbackType;
    private Long positionSeconds;
    private Long durationSeconds;
    private Boolean completed;
    private LocalDateTime lastPlayedAt;

    private String title;
    private String posterUrl;
    private Integer year;
}
