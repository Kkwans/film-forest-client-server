package com.filmforest.content.dto;

import com.filmforest.content.entity.Tag;

/** 用户端题材筛选所需的最小公开投影。 */
public record GenreOption(Long id, String code, String name, String color) {

    public static GenreOption from(Tag tag) {
        return new GenreOption(tag.getId(), tag.getCode(), tag.getName(), tag.getColor());
    }
}
