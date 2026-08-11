package com.filmforest.content.service;

import com.filmforest.content.dto.UserPlaybackHistoryRequest;
import com.filmforest.content.dto.UserPlaybackHistoryView;

import java.util.List;

/** 跨设备播放历史业务服务。 */
public interface UserPlaybackHistoryService {

    List<UserPlaybackHistoryView> list(Long userId, int limit);

    void upsert(Long userId, UserPlaybackHistoryRequest request);

    void remove(Long userId, String contentType, Long contentId);

    void clear(Long userId);
}
