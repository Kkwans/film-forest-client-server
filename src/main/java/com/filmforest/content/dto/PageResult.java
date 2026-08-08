package com.filmforest.content.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/** 稳定的公开分页响应，避免把 ORM 分页对象直接暴露给前端。 */
public record PageResult<T>(
        List<T> records,
        long total,
        long size,
        long current,
        long pages
) {
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent(), page.getPages());
    }
}
