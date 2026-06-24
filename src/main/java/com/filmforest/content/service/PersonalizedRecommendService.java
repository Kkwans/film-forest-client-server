package com.filmforest.content.service;

import java.util.List;
import java.util.Map;

/**
 * 个性化推荐服务接口
 */
public interface PersonalizedRecommendService {

    /**
     * 基于用户偏好推荐内容
     * @param genres     偏好类型，逗号分隔
     * @param region     偏好地区（可选）
     * @param excludeIds 排除的 ID，逗号分隔（已看过/收藏的）
     * @param limit      返回数量
     * @return 推荐列表（跨类型混合）
     */
    List<Map<String, Object>> getPersonalized(String genres, String region, String excludeIds, int limit);
}
