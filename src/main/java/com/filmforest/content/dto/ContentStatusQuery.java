package com.filmforest.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 单个用户内容状态查询项。 */
public record ContentStatusQuery(
        @NotBlank(message = "contentType 不能为空") String contentType,
        @NotNull(message = "contentId 不能为空") Long contentId
) {
}
