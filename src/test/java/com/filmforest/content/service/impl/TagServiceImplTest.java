package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.entity.TagContentType;
import com.filmforest.content.mapper.ContentTagMapper;
import com.filmforest.content.mapper.TagContentTypeMapper;
import com.filmforest.content.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

    @Mock private ContentTagMapper contentTagMapper;
    @Mock private TagContentTypeMapper tagContentTypeMapper;
    @Mock private TagMapper tagMapper;

    private TagServiceImpl service;

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(Tag.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), "tag-service-test"),
                    Tag.class);
        }
        service = new TagServiceImpl(contentTagMapper, tagContentTypeMapper);
        ReflectionTestUtils.setField(service, "baseMapper", tagMapper);
    }

    @Test
    void standardGenresUseCanonicalTypeMappingsAndSystemTagsOnly() {
        TagContentType first = mapping(7L, "short_drama");
        TagContentType second = mapping(9L, "short_drama");
        Tag sweet = tag(7L, "甜宠");
        Tag revenge = tag(9L, "复仇");
        when(tagContentTypeMapper.selectList(any())).thenReturn(List.of(first, second));
        when(tagMapper.selectList(any())).thenReturn(List.of(sweet, revenge));

        assertThat(service.getStandardGenres("short"))
                .extracting(Tag::getName)
                .containsExactly("甜宠", "复仇");
    }

    @Test
    void standardGenresReturnEmptyWhenTypeHasNoMappings() {
        when(tagContentTypeMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.getStandardGenres("movie")).isEmpty();
    }

    private static TagContentType mapping(long tagId, String contentType) {
        TagContentType mapping = new TagContentType();
        mapping.setTagId(tagId);
        mapping.setContentType(contentType);
        return mapping;
    }

    private static Tag tag(long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSystem(1);
        return tag;
    }
}
