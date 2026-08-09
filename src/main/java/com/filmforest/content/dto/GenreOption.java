package com.filmforest.content.dto;

/** 用户端题材筛选所需的最小公开投影。 */
public record GenreOption(Long id, String code, String name, String color, long contentCount) {}
