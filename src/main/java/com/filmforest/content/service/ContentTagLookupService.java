package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.filmforest.content.entity.ContentTag;
import com.filmforest.content.mapper.ContentTagMapper;
import com.filmforest.content.model.ContentType;
import org.springframework.stereotype.Service;

import java.util.List;

/** 将标签关联安全地应用到五类内容查询。 */
@Service
public class ContentTagLookupService {

    private final ContentTagMapper contentTagMapper;

    public ContentTagLookupService(ContentTagMapper contentTagMapper) {
        this.contentTagMapper = contentTagMapper;
    }

    public <T> void apply(
            LambdaQueryWrapper<T> wrapper,
            SFunction<T, ?> idColumn,
            Long tagId,
            ContentType contentType) {
        if (tagId == null) {
            return;
        }
        List<Long> ids = contentTagMapper.selectList(new LambdaQueryWrapper<ContentTag>()
                        .select(ContentTag::getContentId)
                        .eq(ContentTag::getTagId, tagId)
                        .eq(ContentTag::getContentType, contentType.code()))
                .stream()
                .map(ContentTag::getContentId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in(idColumn, ids);
        }
    }
}
