package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.filmforest.content.entity.UserPlaybackHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 用户播放历史数据访问层。 */
@Mapper
public interface UserPlaybackHistoryMapper extends BaseMapper<UserPlaybackHistory> {

    @Select("""
            SELECT id, user_id, content_type, content_id, resource_online_id,
                   episode_number, episode_title, source_name, playback_type,
                   position_seconds, duration_seconds, completed, last_played_at,
                   created_at, updated_at
              FROM user_playback_history
             WHERE user_id = #{userId}
             ORDER BY last_played_at DESC, id DESC
             LIMIT #{limit}
            """)
    List<UserPlaybackHistory> selectByUserOrderByLastPlayed(@Param("userId") Long userId,
                                                            @Param("limit") int limit);

    /**
     * 原子新增/更新同一用户同一内容的播放进度。
     * last_played_at 由数据库服务器生成，避免信任客户端时钟。
     */
    @Insert("""
            INSERT INTO user_playback_history
                (user_id, content_type, content_id, resource_online_id,
                 episode_number, episode_title, source_name, playback_type,
                 position_seconds, duration_seconds, completed,
                 last_played_at, created_at, updated_at)
            VALUES
                (#{history.userId}, #{history.contentType}, #{history.contentId}, #{history.resourceOnlineId},
                 #{history.episodeNumber}, #{history.episodeTitle}, #{history.sourceName}, #{history.playbackType},
                 #{history.positionSeconds}, #{history.durationSeconds}, #{history.completed},
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                 resource_online_id = VALUES(resource_online_id),
                 episode_number = VALUES(episode_number),
                 episode_title = VALUES(episode_title),
                 source_name = VALUES(source_name),
                 playback_type = VALUES(playback_type),
                 position_seconds = VALUES(position_seconds),
                 duration_seconds = VALUES(duration_seconds),
                 completed = VALUES(completed),
                 last_played_at = CURRENT_TIMESTAMP,
                 updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(@Param("history") UserPlaybackHistory history);

    @Delete("""
            DELETE FROM user_playback_history
             WHERE user_id = #{userId}
               AND content_type = #{contentType}
               AND content_id = #{contentId}
            """)
    int deleteByUserAndContent(@Param("userId") Long userId,
                               @Param("contentType") String contentType,
                               @Param("contentId") Long contentId);

    @Delete("""
            DELETE FROM user_playback_history
             WHERE user_id = #{userId}
            """)
    int deleteByUserId(@Param("userId") Long userId);
}
