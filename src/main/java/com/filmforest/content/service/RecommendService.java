package com.filmforest.content.service;

import java.util.List;
import java.util.Map;

/**
 * 推荐服务接口
 * 提供首页推荐数据：热门内容 + 最新更新
 */
public interface RecommendService {

    /**
     * 获取首页推荐数据
     * @param topN 每个分类返回的条目数
     * @return 推荐数据，包含 hot 和 latest 两个分组
     *         key: "hot" / "latest"
     *         value: 按类型分组的推荐列表
     */
    Map<String, Map<String, List<Map<String, Object>>>> getRecommendations(int topN);
}
