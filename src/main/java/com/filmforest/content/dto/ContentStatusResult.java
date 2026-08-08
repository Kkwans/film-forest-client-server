package com.filmforest.content.dto;

import java.util.List;
import java.util.Map;

/** 混合内容批量状态查询结果，使用类型与 ID 共同标识内容。 */
public record ContentStatusResult(
        String contentType,
        Long contentId,
        List<Map<String, Object>> statuses
) {
}
