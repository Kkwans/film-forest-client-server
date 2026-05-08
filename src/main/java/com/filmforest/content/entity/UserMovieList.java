package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户片单表
 */
@Data
@TableName("user_movie_list")
public class UserMovieList {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;            // 用户ID
    private String name;            // 片单名称
    private String type;            // 类型：want_to_watch/watching/watched/custom
    private String description;     // 描述
    private Integer isDefault;      // 是否默认片单：0=否 1=是

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
