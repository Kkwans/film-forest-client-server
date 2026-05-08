package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 片单明细表
 */
@Data
@TableName("user_movie_list_item")
public class UserMovieListItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long listId;            // 片单ID
    private Long movieId;           // 影视ID
    private String contentType;     // 内容类型：movie/drama/variety/anime/short_drama

    private BigDecimal rating;           // 评分(10分制)
    private String note;                 // 备注/感受

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime addedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
