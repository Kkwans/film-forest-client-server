-- ============================================================
-- 影视森林 - 性能优化索引迁移
-- 创建时间: 2026-05-14
-- 说明: 为高频查询字段添加索引，优化查询性能
-- ============================================================

-- 1. 用户片单表：user_id 查询索引
CREATE INDEX IF NOT EXISTS idx_user_movie_list_user_id ON user_movie_list(user_id);
CREATE INDEX IF NOT EXISTS idx_user_movie_list_user_type ON user_movie_list(user_id, type);

-- 2. 片单条目表：核心查询路径索引
-- list_id 是最频繁的查询条件
CREATE INDEX IF NOT EXISTS idx_item_list_id ON user_movie_list_item(list_id);
-- (list_id, movie_id, content_type) 组合索引：用于条目去重和状态查询
CREATE INDEX IF NOT EXISTS idx_item_list_movie_type ON user_movie_list_item(list_id, movie_id, content_type);
-- (movie_id, content_type) 索引：用于批量状态查询
CREATE INDEX IF NOT EXISTS idx_item_movie_type ON user_movie_list_item(movie_id, content_type);

-- 3. 电影表：搜索和排序索引
CREATE INDEX IF NOT EXISTS idx_movie_year ON movie(year);
CREATE INDEX IF NOT EXISTS idx_movie_score_douban ON movie(score_douban);
CREATE INDEX IF NOT EXISTS idx_movie_score_imdb ON movie(score_imdb);
CREATE INDEX IF NOT EXISTS idx_movie_updated_at ON movie(updated_at);
-- title 前缀索引（搜索优化）
CREATE INDEX IF NOT EXISTS idx_movie_title ON movie(title(20));

-- 4. 剧集表
CREATE INDEX IF NOT EXISTS idx_drama_year ON drama(year);
CREATE INDEX IF NOT EXISTS idx_drama_score_douban ON drama(score_douban);
CREATE INDEX IF NOT EXISTS idx_drama_updated_at ON drama(updated_at);
CREATE INDEX IF NOT EXISTS idx_drama_title ON drama(title(20));

-- 5. 综艺表
CREATE INDEX IF NOT EXISTS idx_variety_year ON variety(year);
CREATE INDEX IF NOT EXISTS idx_variety_score_douban ON variety(score_douban);
CREATE INDEX IF NOT EXISTS idx_variety_updated_at ON variety(updated_at);
CREATE INDEX IF NOT EXISTS idx_variety_title ON variety(title(20));

-- 6. 动漫表
CREATE INDEX IF NOT EXISTS idx_anime_year ON anime(year);
CREATE INDEX IF NOT EXISTS idx_anime_score_douban ON anime(score_douban);
CREATE INDEX IF NOT EXISTS idx_anime_updated_at ON anime(updated_at);
CREATE INDEX IF NOT EXISTS idx_anime_title ON anime(title(20));

-- 7. 短剧表
CREATE INDEX IF NOT EXISTS idx_short_drama_year ON short_drama(year);
CREATE INDEX IF NOT EXISTS idx_short_drama_score_douban ON short_drama(score_douban);
CREATE INDEX IF NOT EXISTS idx_short_drama_updated_at ON short_drama(updated_at);
CREATE INDEX IF NOT EXISTS idx_short_drama_title ON short_drama(title(20));
