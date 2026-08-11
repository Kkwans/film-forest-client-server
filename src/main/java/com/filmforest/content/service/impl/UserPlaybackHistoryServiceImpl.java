package com.filmforest.content.service.impl;

import com.filmforest.content.dto.UserPlaybackHistoryRequest;
import com.filmforest.content.dto.UserPlaybackHistoryView;
import com.filmforest.content.entity.Anime;
import com.filmforest.content.entity.Drama;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.entity.ShortDrama;
import com.filmforest.content.entity.UserPlaybackHistory;
import com.filmforest.content.entity.Variety;
import com.filmforest.content.mapper.AnimeMapper;
import com.filmforest.content.mapper.DramaMapper;
import com.filmforest.content.mapper.MovieMapper;
import com.filmforest.content.mapper.ShortDramaMapper;
import com.filmforest.content.mapper.UserPlaybackHistoryMapper;
import com.filmforest.content.mapper.VarietyMapper;
import com.filmforest.content.model.ContentStatus;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.service.PublishedContentAccessService;
import com.filmforest.content.service.UserPlaybackHistoryService;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.service.ResourceOnlineService;
import com.filmforest.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 播放历史业务实现，所有展示元数据均从安全的枚举分支读取。 */
@Service
public class UserPlaybackHistoryServiceImpl implements UserPlaybackHistoryService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;
    private static final long MAX_POSITION_SECONDS = 604_800L;

    private final UserPlaybackHistoryMapper historyMapper;
    private final PublishedContentAccessService publishedContentAccessService;
    private final ResourceOnlineService resourceOnlineService;
    private final MovieMapper movieMapper;
    private final DramaMapper dramaMapper;
    private final VarietyMapper varietyMapper;
    private final AnimeMapper animeMapper;
    private final ShortDramaMapper shortDramaMapper;

    public UserPlaybackHistoryServiceImpl(
            UserPlaybackHistoryMapper historyMapper,
            PublishedContentAccessService publishedContentAccessService,
            ResourceOnlineService resourceOnlineService,
            MovieMapper movieMapper,
            DramaMapper dramaMapper,
            VarietyMapper varietyMapper,
            AnimeMapper animeMapper,
            ShortDramaMapper shortDramaMapper) {
        this.historyMapper = historyMapper;
        this.publishedContentAccessService = publishedContentAccessService;
        this.resourceOnlineService = resourceOnlineService;
        this.movieMapper = movieMapper;
        this.dramaMapper = dramaMapper;
        this.varietyMapper = varietyMapper;
        this.animeMapper = animeMapper;
        this.shortDramaMapper = shortDramaMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserPlaybackHistoryView> list(Long userId, int limit) {
        requireUser(userId);
        validateLimit(limit);

        List<UserPlaybackHistory> histories = historyMapper.selectByUserOrderByLastPlayed(userId, limit);
        if (histories == null || histories.isEmpty()) {
            return List.of();
        }

        List<UserPlaybackHistoryView> result = new ArrayList<>(histories.size());
        for (UserPlaybackHistory history : histories) {
            ContentType contentType = parseStoredContentType(history.getContentType());
            if (contentType == null) {
                continue;
            }
            ContentMetadata metadata = loadPublishedMetadata(contentType, history.getContentId());
            // 下线或逻辑删除内容不会出现在播放历史中。
            if (metadata == null) {
                continue;
            }
            result.add(toView(history, contentType, metadata));
        }
        return result;
    }

    @Override
    @Transactional
    public void upsert(Long userId, UserPlaybackHistoryRequest request) {
        requireUser(userId);
        if (request == null) {
            throw new BusinessException("请求体不能为空");
        }
        ContentType contentType = ContentType.parse(request.getContentType());
        Long contentId = request.getContentId();
        Long positionSeconds = request.getPositionSeconds();
        if (contentId == null || contentId <= 0) {
            throw new BusinessException("contentId 必须为正数");
        }
        if (positionSeconds == null || positionSeconds < 0) {
            throw new BusinessException("positionSeconds 不能为负数且不能为空");
        }
        if (positionSeconds > MAX_POSITION_SECONDS) {
            throw new BusinessException("positionSeconds 不能超过 604800 秒");
        }
        if (request.getDurationSeconds() != null && request.getDurationSeconds() < 0) {
            throw new BusinessException("durationSeconds 不能为负数");
        }
        if (request.getCompleted() == null) {
            throw new BusinessException("completed 不能为空");
        }

        if (!publishedContentAccessService.isPublished(contentType.code(), contentId)) {
            throw new BusinessException("内容尚未上线，不能记录播放历史");
        }

        ResourceOnline resource = request.getResourceId() == null
                ? null
                : requireMatchingResource(request.getResourceId(), contentType, contentId);

        UserPlaybackHistory history = new UserPlaybackHistory();
        history.setUserId(userId);
        history.setContentType(contentType.code());
        history.setContentId(contentId);
        history.setResourceOnlineId(resource == null ? null : resource.getId());
        history.setEpisodeNumber(resource == null ? null : resource.getEpisodeNumber());
        history.setEpisodeTitle(resource == null ? null : resource.getEpisodeTitle());
        history.setSourceName(resource == null ? null : resource.getSourceName());
        history.setPlaybackType(resource == null ? null : resource.getPlaybackType());
        history.setPositionSeconds(positionSeconds);
        history.setDurationSeconds(request.getDurationSeconds());
        history.setCompleted(request.getCompleted());
        // lastPlayedAt/createdAt/updatedAt intentionally remain unset; SQL uses server time.
        historyMapper.upsert(history);
    }

    @Override
    @Transactional
    public void remove(Long userId, String rawContentType, Long contentId) {
        requireUser(userId);
        ContentType contentType = ContentType.parse(rawContentType);
        if (contentId == null || contentId <= 0) {
            throw new BusinessException("contentId 必须为正数");
        }
        historyMapper.deleteByUserAndContent(userId, contentType.code(), contentId);
    }

    @Override
    @Transactional
    public void clear(Long userId) {
        requireUser(userId);
        historyMapper.deleteByUserId(userId);
    }

    private ResourceOnline requireMatchingResource(Long resourceId, ContentType contentType, Long contentId) {
        ResourceOnline resource = resourceOnlineService.getById(resourceId);
        if (!isUsableResource(resource)
                || !Objects.equals(resource.getContentId(), contentId)
                || !sameContentType(resource.getContentType(), contentType)) {
            throw new BusinessException("播放资源不存在、已失效或与内容不匹配");
        }
        return resource;
    }

    private UserPlaybackHistoryView toView(UserPlaybackHistory history,
                                           ContentType contentType,
                                           ContentMetadata metadata) {
        UserPlaybackHistoryView view = new UserPlaybackHistoryView();
        view.setId(history.getId());
        view.setContentType(contentType.code());
        view.setContentId(history.getContentId());
        view.setPositionSeconds(history.getPositionSeconds());
        view.setDurationSeconds(history.getDurationSeconds());
        view.setCompleted(history.getCompleted());
        view.setLastPlayedAt(history.getLastPlayedAt());
        view.setTitle(metadata.title());
        view.setPosterUrl(metadata.posterUrl());
        view.setYear(metadata.year());

        ResourceOnline resource = history.getResourceOnlineId() == null
                ? null
                : resourceOnlineService.getById(history.getResourceOnlineId());
        if (isUsableResource(resource)
                && Objects.equals(resource.getContentId(), history.getContentId())
                && sameContentType(resource.getContentType(), contentType)) {
            // 资源字段均由当前资源派生，历史快照中的文本不作为可信来源。
            view.setResourceId(resource.getId());
            view.setEpisodeNumber(resource.getEpisodeNumber());
            view.setEpisodeTitle(resource.getEpisodeTitle());
            view.setSourceName(resource.getSourceName());
            view.setPlaybackType(resource.getPlaybackType());
        }
        return view;
    }

    private ContentMetadata loadPublishedMetadata(ContentType contentType, Long contentId) {
        if (contentId == null || !publishedContentAccessService.isPublished(contentType.code(), contentId)) {
            return null;
        }
        return switch (contentType) {
            case MOVIE -> {
                Movie content = movieMapper.selectById(contentId);
                yield content == null || !isPublished(content.getStatus(), content.getDeleted())
                        ? null : new ContentMetadata(content.getTitle(), content.getPosterUrl(), content.getYear());
            }
            case DRAMA -> {
                Drama content = dramaMapper.selectById(contentId);
                yield content == null || !isPublished(content.getStatus(), content.getDeleted())
                        ? null : new ContentMetadata(content.getTitle(), content.getPosterUrl(), content.getYear());
            }
            case VARIETY -> {
                Variety content = varietyMapper.selectById(contentId);
                yield content == null || !isPublished(content.getStatus(), content.getDeleted())
                        ? null : new ContentMetadata(content.getTitle(), content.getPosterUrl(), content.getYear());
            }
            case ANIME -> {
                Anime content = animeMapper.selectById(contentId);
                yield content == null || !isPublished(content.getStatus(), content.getDeleted())
                        ? null : new ContentMetadata(content.getTitle(), content.getPosterUrl(), content.getYear());
            }
            case SHORT_DRAMA -> {
                ShortDrama content = shortDramaMapper.selectById(contentId);
                yield content == null || !isPublished(content.getStatus(), content.getDeleted())
                        ? null : new ContentMetadata(content.getTitle(), content.getPosterUrl(), content.getYear());
            }
        };
    }

    private boolean isPublished(Integer status, Integer deleted) {
        return Integer.valueOf(ContentStatus.PUBLISHED.code()).equals(status)
                && !Integer.valueOf(1).equals(deleted);
    }

    private ContentType parseStoredContentType(String rawContentType) {
        try {
            return ContentType.parse(rawContentType);
        } catch (BusinessException ignored) {
            // 历史表中的未知旧数据不能阻断其余有效记录返回。
            return null;
        }
    }

    private boolean sameContentType(String rawResourceType, ContentType expected) {
        try {
            return ContentType.parse(rawResourceType) == expected;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private boolean isUsableResource(ResourceOnline resource) {
        // ResourceOnline 使用 MyBatis-Plus 逻辑删除字段；deleted=1 的资源不会作为有效来源。
        return resource != null && !Integer.valueOf(1).equals(resource.getDeleted());
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(401, "未登录，请先登录");
        }
    }

    private void validateLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new BusinessException("limit 必须在 1 到 100 之间");
        }
    }

    private record ContentMetadata(String title, String posterUrl, Integer year) {
    }
}
