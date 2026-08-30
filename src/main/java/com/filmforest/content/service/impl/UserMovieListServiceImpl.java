package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.filmforest.content.dto.UserListItemPageRow;
import com.filmforest.content.dto.UserListItemVO;
import com.filmforest.content.dto.UserDefaultListView;
import com.filmforest.content.dto.ContentStatusQuery;
import com.filmforest.content.dto.ContentStatusResult;
import com.filmforest.content.entity.*;
import com.filmforest.content.mapper.*;
import com.filmforest.content.service.UserMovieListService;
import com.filmforest.content.service.PublishedContentAccessService;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.model.ContentStatus;
import com.filmforest.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserMovieListServiceImpl extends ServiceImpl<UserMovieListMapper, UserMovieList> implements UserMovieListService {

    private final UserMovieListItemMapper itemMapper;
    private final MovieMapper movieMapper;
    private final DramaMapper dramaMapper;
    private final VarietyMapper varietyMapper;
    private final AnimeMapper animeMapper;
    private final ShortDramaMapper shortDramaMapper;
    private final PublishedContentAccessService publishedContentAccessService;

    public UserMovieListServiceImpl(UserMovieListItemMapper itemMapper,
                                    MovieMapper movieMapper,
                                    DramaMapper dramaMapper,
                                    VarietyMapper varietyMapper,
                                    AnimeMapper animeMapper,
                                    ShortDramaMapper shortDramaMapper,
                                    PublishedContentAccessService publishedContentAccessService) {
        this.itemMapper = itemMapper;
        this.movieMapper = movieMapper;
        this.dramaMapper = dramaMapper;
        this.varietyMapper = varietyMapper;
        this.animeMapper = animeMapper;
        this.shortDramaMapper = shortDramaMapper;
        this.publishedContentAccessService = publishedContentAccessService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDefaultLists(Long userId) {
        String[][] defaults = {
                {"想看", "want_to_watch"},
                {"在看", "watching"},
                {"看过", "watched"}
        };

        for (String[] pair : defaults) {
            UserMovieList list = new UserMovieList();
            list.setUserId(userId);
            list.setName(pair[0]);
            list.setType(pair[1]);
            list.setIsDefault(1);
            save(list);
        }
    }

    @Override
    public List<UserMovieList> getUserLists(Long userId) {
        List<UserMovieList> lists = list(new LambdaQueryWrapper<UserMovieList>()
                .eq(UserMovieList::getUserId, userId)
                .orderByAsc(UserMovieList::getIsDefault)
                .orderByDesc(UserMovieList::getCreatedAt));

        // 仅统计仍处于已上线状态的内容；草稿/下线内容不得通过数量侧信道暴露。
        if (!lists.isEmpty()) {
            List<Long> listIds = lists.stream().map(UserMovieList::getId).collect(Collectors.toList());
            List<UserMovieListItem> items = itemMapper.selectList(new LambdaQueryWrapper<UserMovieListItem>()
                    .in(UserMovieListItem::getListId, listIds));
            Map<Long, Integer> countMap = enrichItems(items).stream()
                    .collect(Collectors.groupingBy(UserListItemVO::getListId, Collectors.summingInt(ignored -> 1)));
            for (UserMovieList list : lists) {
                list.setItemCount(countMap.getOrDefault(list.getId(), 0));
            }
        }

        return lists;
    }

    @Override
    public List<UserDefaultListView> getDefaultUserLists(Long userId) {
        return list(new LambdaQueryWrapper<UserMovieList>()
                .select(UserMovieList::getId, UserMovieList::getName, UserMovieList::getType)
                .eq(UserMovieList::getUserId, userId)
                .eq(UserMovieList::getIsDefault, 1)
                .orderByAsc(UserMovieList::getId))
                .stream()
                .map(list -> new UserDefaultListView(list.getId(), list.getName(), list.getType()))
                .toList();
    }

    @Override
    public UserMovieList createList(Long userId, String name, String description) {
        String normalizedName = validateListName(name);
        String normalizedDescription = validateDescription(description);
        UserMovieList list = new UserMovieList();
        list.setUserId(userId);
        list.setName(normalizedName);
        list.setType("custom");
        list.setDescription(normalizedDescription);
        list.setIsDefault(0);
        list.setItemCount(0);
        save(list);
        return list;
    }

    @Override
    public void updateList(Long userId, Long listId, String name, String description) {
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }
        if (list.getIsDefault() != null && list.getIsDefault() == 1) {
            throw new RuntimeException("默认片单不可编辑");
        }
        if (name != null) {
            list.setName(validateListName(name));
        }
        if (description != null) {
            list.setDescription(validateDescription(description));
        }
        updateById(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteList(Long userId, Long listId) {
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }
        if (list.getIsDefault() != null && list.getIsDefault() == 1) {
            throw new RuntimeException("默认片单不可删除");
        }

        // 先删除片单记录，再删除条目 —— 减少与 addItem 的竞态窗口
        // 并发的 addItem 在 getById 时会因片单已删除而直接失败
        removeById(listId);
        // 再删除片单下的所有条目（孤儿数据清理）
        itemMapper.delete(new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, Long listId, Long movieId, String contentType, BigDecimal rating, String note) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }
        ContentType normalizedType = ContentType.parse(contentType);
        if (!publishedContentAccessService.isPublished(normalizedType.code(), movieId)) {
            throw new BusinessException("内容不存在或尚未上线");
        }
        validateRating(list, rating);
        String normalizedNote = validateNote(note);

        // 先尝试直接插入，利用 UNIQUE 约束 (list_id, movie_id, content_type) 防止并发重复
        UserMovieListItem item = new UserMovieListItem();
        item.setListId(listId);
        item.setMovieId(movieId);
        item.setContentType(normalizedType.code());
        item.setRating(rating);
        item.setNote(normalizedNote);
        if ("watched".equals(list.getType())) {
            item.setWatchedAt(LocalDateTime.now(ZoneOffset.UTC));
        }

        try {
            itemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 唯一约束冲突 = 已存在，转为更新评分和备注
            UserMovieListItem existing = itemMapper.selectOne(new LambdaQueryWrapper<UserMovieListItem>()
                    .eq(UserMovieListItem::getListId, listId)
                    .eq(UserMovieListItem::getMovieId, movieId)
                    .eq(UserMovieListItem::getContentType, normalizedType.code()));
            if (existing != null) {
                if (rating != null) existing.setRating(rating);
                if (note != null) existing.setNote(normalizedNote);
                // Duplicate adds/editing evaluation must never reset the original watchedAt.
                itemMapper.updateById(existing);
            }
            // 注意：不在此处 return，继续执行互斥逻辑
        }

        // 互斥逻辑：
        // 1. 添加到在看/看过 → 自动从想看删除
        // 2. 添加到看过 → 自动从在看删除
        if ("watching".equals(list.getType()) || "watched".equals(list.getType())) {
            UserMovieList wantList = getOne(new LambdaQueryWrapper<UserMovieList>()
                    .eq(UserMovieList::getUserId, userId)
                    .eq(UserMovieList::getType, "want_to_watch")
                    .last("LIMIT 1"));
            if (wantList != null) {
                itemMapper.delete(new LambdaQueryWrapper<UserMovieListItem>()
                        .eq(UserMovieListItem::getListId, wantList.getId())
                        .eq(UserMovieListItem::getMovieId, movieId)
                        .eq(UserMovieListItem::getContentType, normalizedType.code()));
            }
        }
        if ("watched".equals(list.getType())) {
            UserMovieList watchingList = getOne(new LambdaQueryWrapper<UserMovieList>()
                    .eq(UserMovieList::getUserId, userId)
                    .eq(UserMovieList::getType, "watching")
                    .last("LIMIT 1"));
            if (watchingList != null) {
                itemMapper.delete(new LambdaQueryWrapper<UserMovieListItem>()
                        .eq(UserMovieListItem::getListId, watchingList.getId())
                        .eq(UserMovieListItem::getMovieId, movieId)
                        .eq(UserMovieListItem::getContentType, normalizedType.code()));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, Long listId, Long movieId, String contentType) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }

        itemMapper.delete(new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId)
                .eq(UserMovieListItem::getMovieId, movieId)
                .eq(UserMovieListItem::getContentType, ContentType.parse(contentType).code()));
    }

    @Override
    public IPage<UserListItemVO> getListItems(Long userId, Long listId, int pageNum, int pageSize,
                                              String sort, String sortDir, String contentType) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }

        String normalizedSort = normalizeListSort(sort);
        boolean desc = "desc".equalsIgnoreCase(sortDir);
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.max(pageSize, 1);
        long offset = (long) (safePage - 1) * safeSize;

        // 先在数据库过滤已上线内容并统计完整可见集合，再只 enrich 当前页投影，
        // 避免把整张片单加载到 JVM 后排序和切片。
        long total = itemMapper.countVisible(listId, contentType);
        List<UserListItemPageRow> rows = itemMapper.selectVisiblePage(
                listId, contentType, normalizedSort, desc, safeSize, offset);
        List<UserListItemVO> records = rows == null ? Collections.emptyList()
                : rows.stream().map(this::toUserListItemVO).toList();

        Page<UserListItemVO> voPage = new Page<>(safePage, safeSize, total);
        voPage.setRecords(records);
        return voPage;
    }

    private String normalizeListSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "addedAt";
        }
        String normalized = sort.trim();
        if (Set.of("addedAt", "userRating", "year", "douban").contains(normalized)) {
            return normalized;
        }
        throw new BusinessException(400, "不支持的片单排序方式: " + sort);
    }

    private UserListItemVO toUserListItemVO(UserListItemPageRow row) {
        UserListItemVO vo = new UserListItemVO();
        vo.setId(row.getId());
        vo.setListId(row.getListId());
        vo.setMovieId(row.getMovieId());
        vo.setContentType(row.getContentType());
        vo.setAddedAt(toUtcOffset(row.getAddedAt()));
        vo.setWatchedAt(toUtcOffset(row.getWatchedAt()));
        vo.setTitle(row.getTitle());
        vo.setCover(row.getCover());
        vo.setAlias(row.getAlias());
        vo.setYear(row.getYear());
        vo.setRating(row.getRating());
        vo.setScoreDoubanCount(row.getScoreDoubanCount());
        vo.setScoreImdbCount(row.getScoreImdbCount());
        vo.setScoreRtCriticCount(row.getScoreRtCriticCount());
        vo.setScoreRtAudienceCount(row.getScoreRtAudienceCount());
        vo.setRegion(row.getRegion());
        vo.setGenre(row.getGenre());
        vo.setDirector(row.getDirector());
        vo.setWriter(row.getWriter());
        vo.setActor(row.getActor());
        vo.setReleaseDate(row.getReleaseDate());
        vo.setDuration(row.getDuration());
        vo.setTotalEpisode(row.getTotalEpisode());
        vo.setUserRating(row.getUserRating());
        vo.setNote(row.getNote());
        return vo;
    }

    /**
     * 批量为片单条目填充影视基本信息（解决 N+1 查询问题）
     * 按 contentType 分组，每组只发一次批量查询
     */
    private List<UserListItemVO> enrichItems(List<UserMovieListItem> items) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        // 按 contentType 分组收集需要查询的 ID
        Map<String, List<Long>> idsByType = new HashMap<>();
        for (UserMovieListItem item : items) {
            idsByType.computeIfAbsent(item.getContentType(), k -> new ArrayList<>()).add(item.getMovieId());
        }

        // 批量查询每种类型的内容（每种类型最多 1 次 IN 查询，替代 N 次 selectById）
        Map<Long, Movie> movieMap = Collections.emptyMap();
        Map<Long, Drama> dramaMap = Collections.emptyMap();
        Map<Long, Variety> varietyMap = Collections.emptyMap();
        Map<Long, Anime> animeMap = Collections.emptyMap();
        Map<Long, ShortDrama> shortDramaMap = Collections.emptyMap();

        if (idsByType.containsKey("movie")) {
            List<Long> ids = idsByType.get("movie");
            movieMap = movieMapper.selectList(new LambdaQueryWrapper<Movie>()
                            .in(Movie::getId, ids)
                            .eq(Movie::getStatus, ContentStatus.PUBLISHED.code())).stream()
                    .collect(Collectors.toMap(Movie::getId, Function.identity(), (a, b) -> a));
        }
        if (idsByType.containsKey("drama")) {
            List<Long> ids = idsByType.get("drama");
            dramaMap = dramaMapper.selectList(new LambdaQueryWrapper<Drama>()
                            .in(Drama::getId, ids)
                            .eq(Drama::getStatus, ContentStatus.PUBLISHED.code())).stream()
                    .collect(Collectors.toMap(Drama::getId, Function.identity(), (a, b) -> a));
        }
        if (idsByType.containsKey("variety")) {
            List<Long> ids = idsByType.get("variety");
            varietyMap = varietyMapper.selectList(new LambdaQueryWrapper<Variety>()
                            .in(Variety::getId, ids)
                            .eq(Variety::getStatus, ContentStatus.PUBLISHED.code())).stream()
                    .collect(Collectors.toMap(Variety::getId, Function.identity(), (a, b) -> a));
        }
        if (idsByType.containsKey("anime")) {
            List<Long> ids = idsByType.get("anime");
            animeMap = animeMapper.selectList(new LambdaQueryWrapper<Anime>()
                            .in(Anime::getId, ids)
                            .eq(Anime::getStatus, ContentStatus.PUBLISHED.code())).stream()
                    .collect(Collectors.toMap(Anime::getId, Function.identity(), (a, b) -> a));
        }
        if (idsByType.containsKey("short_drama")) {
            List<Long> ids = idsByType.get("short_drama");
            shortDramaMap = shortDramaMapper.selectList(new LambdaQueryWrapper<ShortDrama>()
                            .in(ShortDrama::getId, ids)
                            .eq(ShortDrama::getStatus, ContentStatus.PUBLISHED.code())).stream()
                    .collect(Collectors.toMap(ShortDrama::getId, Function.identity(), (a, b) -> a));
        }

        // 组装 VO
        List<UserListItemVO> result = new ArrayList<>(items.size());
        for (UserMovieListItem item : items) {
            UserListItemVO visibleItem = enrichItem(item, movieMap, dramaMap, varietyMap, animeMap, shortDramaMap);
            if (visibleItem != null) {
                result.add(visibleItem);
            }
        }
        return result;
    }

    /**
     * 为单个片单条目填充影视基本信息（从预加载的 Map 中获取，无额外查询）
     */
    private UserListItemVO enrichItem(UserMovieListItem item,
                                       Map<Long, Movie> movieMap,
                                       Map<Long, Drama> dramaMap,
                                       Map<Long, Variety> varietyMap,
                                       Map<Long, Anime> animeMap,
                                       Map<Long, ShortDrama> shortDramaMap) {
        UserListItemVO vo = new UserListItemVO();
        vo.setId(item.getId());
        vo.setListId(item.getListId());
        vo.setMovieId(item.getMovieId());
        vo.setContentType(item.getContentType());
        vo.setAddedAt(toUtcOffset(item.getAddedAt()));
        vo.setWatchedAt(toUtcOffset(item.getWatchedAt()));
        vo.setUserRating(item.getRating());
        vo.setNote(item.getNote());

        String ct = item.getContentType();
        Long movieId = item.getMovieId();
        boolean published = switch (ct) {
            case "movie" -> movieMap.containsKey(movieId);
            case "drama" -> dramaMap.containsKey(movieId);
            case "variety" -> varietyMap.containsKey(movieId);
            case "anime" -> animeMap.containsKey(movieId);
            case "short_drama" -> shortDramaMap.containsKey(movieId);
            default -> false;
        };
        if (!published) {
            return null;
        }

        if ("movie".equals(ct)) {
            Movie m = movieMap.get(movieId);
            if (m != null) {
                vo.setTitle(m.getTitle());
                vo.setCover(m.getPosterUrl());
                vo.setAlias(m.getAlias());
                vo.setYear(m.getYear());
                vo.setRating(m.getScoreDouban());
                vo.setScoreDoubanCount(m.getScoreDoubanCount());
                vo.setScoreImdbCount(m.getScoreImdbCount());
                vo.setScoreRtCriticCount(m.getScoreRtCriticCount());
                vo.setScoreRtAudienceCount(m.getScoreRtAudienceCount());
                vo.setRegion(m.getRegion());
                vo.setGenre(m.getGenre());
                vo.setDirector(m.getDirector());
                vo.setWriter(m.getWriter());
                vo.setActor(m.getActor());
                vo.setReleaseDate(m.getReleaseDate());
                vo.setDuration(m.getDuration());
            }
        } else if ("drama".equals(ct)) {
            Drama d = dramaMap.get(movieId);
            if (d != null) {
                vo.setTitle(d.getTitle());
                vo.setCover(d.getPosterUrl());
                vo.setAlias(d.getAlias());
                vo.setYear(d.getYear());
                vo.setRating(d.getScoreDouban());
                vo.setScoreDoubanCount(d.getScoreDoubanCount());
                vo.setScoreImdbCount(d.getScoreImdbCount());
                vo.setRegion(d.getRegion());
                vo.setGenre(d.getGenre());
                vo.setDirector(d.getDirector());
                vo.setWriter(d.getWriter());
                vo.setActor(d.getActor());
                vo.setReleaseDate(d.getReleaseDate());
                vo.setTotalEpisode(d.getTotalEpisode());
            }
        } else if ("variety".equals(ct)) {
            Variety v = varietyMap.get(movieId);
            if (v != null) {
                vo.setTitle(v.getTitle());
                vo.setCover(v.getPosterUrl());
                vo.setAlias(v.getAlias());
                vo.setYear(v.getYear());
                vo.setRating(v.getScoreDouban());
                vo.setScoreDoubanCount(v.getScoreDoubanCount());
                vo.setScoreImdbCount(v.getScoreImdbCount());
                vo.setRegion(v.getRegion());
                vo.setGenre(v.getGenre());
                vo.setDirector(v.getDirector());
                vo.setWriter(v.getWriter());
                vo.setActor(v.getActor());
                vo.setReleaseDate(v.getReleaseDate());
                vo.setTotalEpisode(v.getTotalEpisode());
            }
        } else if ("anime".equals(ct)) {
            Anime a = animeMap.get(movieId);
            if (a != null) {
                vo.setTitle(a.getTitle());
                vo.setCover(a.getPosterUrl());
                vo.setAlias(a.getAlias());
                vo.setYear(a.getYear());
                vo.setRating(a.getScoreDouban());
                vo.setScoreDoubanCount(a.getScoreDoubanCount());
                vo.setScoreImdbCount(a.getScoreImdbCount());
                vo.setRegion(a.getRegion());
                vo.setGenre(a.getGenre());
                vo.setDirector(a.getDirector());
                vo.setWriter(a.getWriter());
                vo.setActor(a.getActor());
                vo.setReleaseDate(a.getReleaseDate());
                vo.setTotalEpisode(a.getTotalEpisode());
            }
        } else if ("short_drama".equals(ct)) {
            ShortDrama s = shortDramaMap.get(movieId);
            if (s != null) {
                vo.setTitle(s.getTitle());
                vo.setCover(s.getPosterUrl());
                vo.setAlias(s.getAlias());
                vo.setYear(s.getYear());
                vo.setRating(s.getScoreDouban());
                vo.setScoreDoubanCount(s.getScoreDoubanCount());
                vo.setScoreImdbCount(s.getScoreImdbCount());
                vo.setRegion(s.getRegion());
                vo.setGenre(s.getGenre());
                vo.setDirector(s.getDirector());
                vo.setWriter(s.getWriter());
                vo.setActor(s.getActor());
                vo.setReleaseDate(s.getReleaseDate());
                vo.setDuration(s.getDuration());
                vo.setTotalEpisode(s.getTotalEpisode());
            }
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long userId, Long listId, Long movieId, String contentType, BigDecimal rating, String note) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }
        ContentType normalizedType = ContentType.parse(contentType);
        if (!publishedContentAccessService.isPublished(normalizedType.code(), movieId)) {
            throw new BusinessException("内容不存在或尚未上线");
        }
        validateRating(list, rating);
        String normalizedNote = validateNote(note);

        UserMovieListItem existing = itemMapper.selectOne(new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId)
                .eq(UserMovieListItem::getMovieId, movieId)
                .eq(UserMovieListItem::getContentType, normalizedType.code()));
        if (existing == null) {
            throw new RuntimeException("条目不存在");
        }
        if (rating != null) existing.setRating(rating);
        if (note != null) existing.setNote(normalizedNote);
        itemMapper.updateById(existing);
    }

    @Override
    public List<Map<String, Object>> getMovieStatus(Long userId, Long movieId, String contentType) {
        ContentType normalizedType = ContentType.parse(contentType);
        if (!publishedContentAccessService.isPublished(normalizedType.code(), movieId)) {
            return Collections.emptyList();
        }
        return getMovieStatusInternal(userId, movieId, normalizedType.code(), null);
    }

    /**
     * 内部方法：查询单个影视在用户片单中的状态
     * @param cachedLists 可选的缓存片单列表，避免重复查询
     */
    private List<Map<String, Object>> getMovieStatusInternal(Long userId, Long movieId, String contentType,
                                                              List<UserMovieList> cachedLists) {
        // 获取用户所有片单（优先使用缓存）
        List<UserMovieList> lists = cachedLists != null ? cachedLists : getUserLists(userId);
        if (lists.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> listIds = lists.stream().map(UserMovieList::getId).collect(Collectors.toList());

        // 查询该影视在哪些片单中
        List<UserMovieListItem> items = itemMapper.selectList(new LambdaQueryWrapper<UserMovieListItem>()
                .in(UserMovieListItem::getListId, listIds)
                .eq(UserMovieListItem::getMovieId, movieId)
                .eq(UserMovieListItem::getContentType, contentType));

        Set<Long> matchedListIds = items.stream()
                .map(UserMovieListItem::getListId)
                .collect(Collectors.toSet());

        Map<Long, UserMovieListItem> itemMap = items.stream()
                .collect(Collectors.toMap(UserMovieListItem::getListId, i -> i, (a, b) -> a));

        return lists.stream().map(list -> {
            Map<String, Object> map = new HashMap<>();
            map.put("listId", list.getId());
            map.put("listName", list.getName());
            map.put("type", list.getType());
            boolean added = matchedListIds.contains(list.getId());
            map.put("added", added);
            if (added) {
                UserMovieListItem item = itemMap.get(list.getId());
                if (item != null) {
                    map.put("userRating", item.getRating());
                    map.put("note", item.getNote());
                    map.put("watchedAt", toUtcOffset(item.getWatchedAt()));
                }
            }
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveItems(Long userId, Long listId, List<Map<String, Object>> items) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }

        if (items == null || items.isEmpty()) {
            return;
        }

        for (Map<String, Object> item : items) {
            Long movieId = item.get("movieId") != null ? Long.valueOf(item.get("movieId").toString()) : null;
            String contentType = (String) item.get("contentType");
            if (movieId != null && contentType != null && !contentType.isBlank()) {
                String normalizedType = ContentType.parse(contentType).code();
                itemMapper.delete(new LambdaQueryWrapper<UserMovieListItem>()
                        .eq(UserMovieListItem::getListId, listId)
                        .eq(UserMovieListItem::getMovieId, movieId)
                        .eq(UserMovieListItem::getContentType, normalizedType));
            }
        }
    }

    @Override
    public Map<Long, List<Map<String, Object>>> getMovieStatusBatch(Long userId, List<Long> movieIds, String contentType) {
        if (movieIds == null || movieIds.isEmpty()) {
            return Collections.emptyMap();
        }

        ContentType normalizedType = ContentType.parse(contentType);
        // 只查一次用户片单，共享给所有 movieId
        List<UserMovieList> lists = getUserLists(userId);
        if (lists.isEmpty()) {
            return movieIds.stream().filter(Objects::nonNull)
                    .collect(Collectors.toMap(Function.identity(), ignored -> Collections.emptyList(),
                            (first, ignored) -> first, LinkedHashMap::new));
        }

        Set<ContentKey> requestedKeys = movieIds.stream()
                .filter(Objects::nonNull)
                .map(movieId -> new ContentKey(normalizedType.code(), movieId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<ContentKey> publishedKeys = findPublishedKeys(requestedKeys);
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Long movieId : movieIds) {
            if (movieId == null || !publishedKeys.contains(new ContentKey(normalizedType.code(), movieId))) {
                result.put(movieId, Collections.emptyList());
            } else {
                result.put(movieId, getMovieStatusInternal(userId, movieId, normalizedType.code(), lists));
            }
        }
        return result;
    }

    @Override
    public List<ContentStatusResult> getContentStatusBatch(Long userId, List<ContentStatusQuery> queries) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }
        if (queries.size() > 200) {
            throw new BusinessException("单次最多查询 200 个内容状态");
        }

        LinkedHashMap<ContentKey, ContentStatusQuery> normalizedQueries = new LinkedHashMap<>();
        for (ContentStatusQuery query : queries) {
            if (query == null || query.contentId() == null) {
                throw new BusinessException("contentId 不能为空");
            }
            ContentType type = ContentType.parse(query.contentType());
            ContentKey key = new ContentKey(type.code(), query.contentId());
            normalizedQueries.putIfAbsent(key, new ContentStatusQuery(type.code(), query.contentId()));
        }

        List<UserMovieList> lists = list(new LambdaQueryWrapper<UserMovieList>()
                .eq(UserMovieList::getUserId, userId)
                .orderByAsc(UserMovieList::getIsDefault)
                .orderByDesc(UserMovieList::getCreatedAt));
        if (lists.isEmpty()) {
            return normalizedQueries.keySet().stream()
                    .map(key -> new ContentStatusResult(key.contentType(), key.contentId(), Collections.emptyList()))
                    .toList();
        }

        List<Long> listIds = lists.stream().map(UserMovieList::getId).toList();
        Set<Long> contentIds = normalizedQueries.keySet().stream().map(ContentKey::contentId).collect(Collectors.toSet());
        Set<String> contentTypes = normalizedQueries.keySet().stream().map(ContentKey::contentType).collect(Collectors.toSet());
        List<UserMovieListItem> items = itemMapper.selectList(new LambdaQueryWrapper<UserMovieListItem>()
                .in(UserMovieListItem::getListId, listIds)
                .in(UserMovieListItem::getMovieId, contentIds)
                .in(UserMovieListItem::getContentType, contentTypes));
        Set<ContentKey> publishedKeys = findPublishedKeys(normalizedQueries.keySet());

        Map<ContentKey, Map<Long, UserMovieListItem>> itemsByContent = items.stream()
                .filter(item -> publishedKeys.contains(new ContentKey(item.getContentType(), item.getMovieId())))
                .collect(Collectors.groupingBy(
                        item -> new ContentKey(item.getContentType(), item.getMovieId()),
                        Collectors.toMap(UserMovieListItem::getListId, Function.identity(), (a, b) -> a)));

        return normalizedQueries.keySet().stream()
                .map(key -> new ContentStatusResult(
                        key.contentType(),
                        key.contentId(),
                        publishedKeys.contains(key)
                                ? buildStatuses(lists, itemsByContent.getOrDefault(key, Collections.emptyMap()))
                                : Collections.emptyList()))
                .toList();
    }

    private Set<ContentKey> findPublishedKeys(Collection<ContentKey> keys) {
        Set<ContentKey> published = new HashSet<>();
        Map<String, Set<Long>> idsByType = keys.stream().collect(Collectors.groupingBy(
                ContentKey::contentType,
                Collectors.mapping(ContentKey::contentId, Collectors.toSet())));
        Set<Long> movieIds = idsByType.getOrDefault(ContentType.MOVIE.code(), Set.of());
        if (!movieIds.isEmpty()) addPublishedKeys(published, ContentType.MOVIE,
                movieMapper.selectList(new LambdaQueryWrapper<Movie>().select(Movie::getId)
                        .in(Movie::getId, movieIds).eq(Movie::getStatus, ContentStatus.PUBLISHED.code()))
                        .stream().map(Movie::getId).toList());
        Set<Long> dramaIds = idsByType.getOrDefault(ContentType.DRAMA.code(), Set.of());
        if (!dramaIds.isEmpty()) addPublishedKeys(published, ContentType.DRAMA,
                dramaMapper.selectList(new LambdaQueryWrapper<Drama>().select(Drama::getId)
                        .in(Drama::getId, dramaIds).eq(Drama::getStatus, ContentStatus.PUBLISHED.code()))
                        .stream().map(Drama::getId).toList());
        Set<Long> varietyIds = idsByType.getOrDefault(ContentType.VARIETY.code(), Set.of());
        if (!varietyIds.isEmpty()) addPublishedKeys(published, ContentType.VARIETY,
                varietyMapper.selectList(new LambdaQueryWrapper<Variety>().select(Variety::getId)
                        .in(Variety::getId, varietyIds).eq(Variety::getStatus, ContentStatus.PUBLISHED.code()))
                        .stream().map(Variety::getId).toList());
        Set<Long> animeIds = idsByType.getOrDefault(ContentType.ANIME.code(), Set.of());
        if (!animeIds.isEmpty()) addPublishedKeys(published, ContentType.ANIME,
                animeMapper.selectList(new LambdaQueryWrapper<Anime>().select(Anime::getId)
                        .in(Anime::getId, animeIds).eq(Anime::getStatus, ContentStatus.PUBLISHED.code()))
                        .stream().map(Anime::getId).toList());
        Set<Long> shortDramaIds = idsByType.getOrDefault(ContentType.SHORT_DRAMA.code(), Set.of());
        if (!shortDramaIds.isEmpty()) addPublishedKeys(published, ContentType.SHORT_DRAMA,
                shortDramaMapper.selectList(new LambdaQueryWrapper<ShortDrama>().select(ShortDrama::getId)
                        .in(ShortDrama::getId, shortDramaIds).eq(ShortDrama::getStatus, ContentStatus.PUBLISHED.code()))
                        .stream().map(ShortDrama::getId).toList());
        return published;
    }

    private void addPublishedKeys(Set<ContentKey> target, ContentType type, List<Long> ids) {
        ids.forEach(id -> target.add(new ContentKey(type.code(), id)));
    }

    private static OffsetDateTime toUtcOffset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private List<Map<String, Object>> buildStatuses(
            List<UserMovieList> lists,
            Map<Long, UserMovieListItem> itemsByList) {
        return lists.stream().map(list -> {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("listId", list.getId());
            status.put("listName", list.getName());
            status.put("type", list.getType());
            UserMovieListItem item = itemsByList.get(list.getId());
            status.put("added", item != null);
            if (item != null) {
                status.put("userRating", item.getRating());
                status.put("note", item.getNote());
                status.put("watchedAt", toUtcOffset(item.getWatchedAt()));
            }
            return status;
        }).toList();
    }

    private String validateListName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("片单名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > 100) {
            throw new BusinessException("片单名称不能超过 100 个字符");
        }
        return normalized;
    }

    private String validateDescription(String description) {
        if (description == null) return null;
        String normalized = description.trim();
        if (normalized.length() > 500) {
            throw new BusinessException("片单描述不能超过 500 个字符");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String validateNote(String note) {
        if (note == null) return null;
        String normalized = note.trim();
        if (normalized.length() > 500) {
            throw new BusinessException("备注不能超过 500 个字符");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateRating(UserMovieList list, BigDecimal rating) {
        if (rating == null) return;
        if (!"watched".equals(list.getType())) {
            throw new BusinessException("只有看过的内容可以评分");
        }
        if (rating.compareTo(BigDecimal.ONE) < 0
                || rating.compareTo(BigDecimal.TEN) > 0
                || rating.remainder(new BigDecimal("0.5")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("评分必须介于 1 和 10 之间，步长为 0.5");
        }
    }

    private record ContentKey(String contentType, Long contentId) {
    }
}
