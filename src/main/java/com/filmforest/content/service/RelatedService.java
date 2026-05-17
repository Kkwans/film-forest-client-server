package com.filmforest.content.service;

import com.filmforest.content.dto.RelatedVO;
import java.util.List;

/**
 * 相关推荐服务接口
 */
public interface RelatedService {

    /**
     * 获取相关推荐内容
     * @param type 内容类型：movie / drama / anime / variety / short_drama
     * @param id   当前内容 ID
     * @param limit 返回数量，默认 6
     * @return 相关推荐列表（同类型、按评分降序、排除自身）
     */
    List<RelatedVO> getRelated(String type, Long id, int limit);
}
