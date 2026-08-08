package com.filmforest.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_poster_setting")
public class UserPosterSetting {

    @TableId(type = IdType.INPUT)
    private Long userId;
    private String posterSource;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String credentialType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private byte[] credentialCiphertext;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private byte[] credentialIv;
    private Integer credentialKeyVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String credentialHint;
    private String validationStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String validationErrorCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime validatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
