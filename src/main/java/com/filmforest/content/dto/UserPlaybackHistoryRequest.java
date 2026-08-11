package com.filmforest.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** 播放历史写入请求。仅接受内容/资源标识与播放进度，不接受客户端展示文本。 */
@Data
public class UserPlaybackHistoryRequest {

    @NotBlank(message = "contentType 不能为空")
    private String contentType;

    @NotNull(message = "contentId 不能为空")
    @Positive(message = "contentId 必须为正数")
    private Long contentId;

    @Positive(message = "resourceId 必须为正数")
    private Long resourceId;

    @NotNull(message = "positionSeconds 不能为空")
    @PositiveOrZero(message = "positionSeconds 不能为负数")
    @Max(value = 604800, message = "positionSeconds 不能超过 604800 秒")
    private Long positionSeconds;

    @PositiveOrZero(message = "durationSeconds 不能为负数")
    private Long durationSeconds;

    @NotNull(message = "completed 不能为空")
    private Boolean completed;
}
