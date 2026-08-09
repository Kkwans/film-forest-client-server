package com.filmforest.content.service;

import com.filmforest.content.model.ContentStatus;
import com.filmforest.content.model.ContentType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 在返回关联数据前统一验证内容是否对用户端公开。 */
@Service
public class PublishedContentAccessService {

    private final JdbcTemplate jdbcTemplate;

    public PublishedContentAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isPublished(String rawContentType, Long contentId) {
        if (contentId == null) {
            return false;
        }
        ContentType contentType = ContentType.parse(rawContentType);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + contentType.code()
                        + "` WHERE id = ? AND status = ? AND is_deleted = 0",
                Integer.class, contentId, ContentStatus.PUBLISHED.code());
        return count != null && count > 0;
    }
}
