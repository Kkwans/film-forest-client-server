package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;        // 用户名
    private String email;           // 邮箱
    private String phone;           // 手机号
    private String passwordHash;    // 密码哈希（BCrypt）
    private String nickname;        // 昵称
    private String avatarUrl;       // 头像URL
    private Integer status;         // 状态：0=禁用 1=正常

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;          // 逻辑删除

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
