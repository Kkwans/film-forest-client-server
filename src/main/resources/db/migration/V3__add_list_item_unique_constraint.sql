-- ============================================================
-- 影视森林 - 片单条目唯一约束
-- 创建时间: 2026-05-15
-- 说明: 防止片单中添加重复条目的并发安全保护
-- ============================================================

-- 先清理可能存在的重复数据（保留 id 最小的那条）
DELETE t1 FROM user_movie_list_item t1
INNER JOIN user_movie_list_item t2
ON t1.list_id = t2.list_id
   AND t1.movie_id = t2.movie_id
   AND t1.content_type = t2.content_type
   AND t1.id > t2.id;

-- 添加唯一约束：同一片单中同一影视（类型+ID）只能有一条记录
ALTER TABLE user_movie_list_item
ADD UNIQUE INDEX uk_list_movie_type (list_id, movie_id, content_type);
