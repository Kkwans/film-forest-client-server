package com.filmforest.content.model;

/** 公开内容生命周期；数值必须与数据库和管理端契约保持一致。 */
public enum ContentStatus {
    DRAFT(0),
    PUBLISHED(1),
    OFFLINE(2);

    private final int code;

    ContentStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
