package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 内容-标签关联表
 */
@Data
@TableName("content_tag")
public class ContentTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contentId;       // 内容ID
    private String contentType;   // 内容类型：movie/drama/variety/anime/short_drama
    private Long tagId;           // 标签ID

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
