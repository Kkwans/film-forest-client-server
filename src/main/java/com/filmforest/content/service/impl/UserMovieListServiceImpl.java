package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.dto.UserListItemVO;
import com.filmforest.content.entity.*;
import com.filmforest.content.mapper.*;
import com.filmforest.content.service.UserMovieListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserMovieListServiceImpl extends ServiceImpl<UserMovieListMapper, UserMovieList> implements UserMovieListService {

    @Autowired
    private UserMovieListItemMapper itemMapper;

    @Autowired
    private MovieMapper movieMapper;

    @Autowired
    private DramaMapper dramaMapper;

    @Autowired
    private VarietyMapper varietyMapper;

    @Autowired
    private AnimeMapper animeMapper;

    @Autowired
    private ShortDramaMapper shortDramaMapper;

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

        // 填充每个片单的 item_count
        if (!lists.isEmpty()) {
            List<Long> listIds = lists.stream().map(UserMovieList::getId).collect(Collectors.toList());
            // 批量查询每个片单的条目数量
            List<Map<String, Object>> counts = itemMapper.selectMaps(
                    new QueryWrapper<UserMovieListItem>()
                            .select("list_id as listId", "count(*) as cnt")
                            .in("list_id", listIds)
                            .groupBy("list_id")
            );
            Map<Long, Integer> countMap = new HashMap<>();
            for (Map<String, Object> row : counts) {
                Long listId = ((Number) row.get("listId")).longValue();
                int cnt = ((Number) row.get("cnt")).intValue();
                countMap.put(listId, cnt);
            }
            for (UserMovieList list : lists) {
                list.setItemCount(countMap.getOrDefault(list.getId(), 0));
            }
        }

        return lists;
    }

    @Override
    public UserMovieList createList(Long userId, String name, String description) {
        UserMovieList list = new UserMovieList();
        list.setUserId(userId);
        list.setName(name);
        list.setType("custom");
        list.setDescription(description);
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
        if (name != null) {
            list.setName(name);
        }
        if (description != null) {
            list.setDescription(description);
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

        // 先尝试直接插入，利用 UNIQUE 约束 (list_id, movie_id, content_type) 防止并发重复
        UserMovieListItem item = new UserMovieListItem();
        item.setListId(listId);
        item.setMovieId(movieId);
        item.setContentType(contentType);
        item.setRating(rating);
        item.setNote(note);

        try {
            itemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            // 唯一约束冲突 = 已存在，转为更新评分和备注
            UserMovieListItem existing = itemMapper.selectOne(new LambdaQueryWrapper<UserMovieListItem>()
                    .eq(UserMovieListItem::getListId, listId)
                    .eq(UserMovieListItem::getMovieId, movieId)
                    .eq(UserMovieListItem::getContentType, contentType));
            if (existing != null) {
                if (rating != null) existing.setRating(rating);
                if (note != null) existing.setNote(note);
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
                        .eq(UserMovieListItem::getContentType, contentType));
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
                        .eq(UserMovieListItem::getContentType, contentType));
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
                .eq(UserMovieListItem::getContentType, contentType));
    }

    @Override
    public IPage<UserListItemVO> getListItems(Long userId, Long listId, int pageNum, int pageSize, String sort, String sortDir) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }

        boolean desc = "desc".equalsIgnoreCase(sortDir);

        // 对于 item 表字段的排序（addedAt, userRating），直接在 SQL 层排序
        if ("addedAt".equals(sort) || "userRating".equals(sort)) {
            Page<UserMovieListItem> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<UserMovieListItem> wrapper = new LambdaQueryWrapper<UserMovieListItem>()
                    .eq(UserMovieListItem::getListId, listId);
            if ("addedAt".equals(sort)) {
                wrapper = desc ? wrapper.orderByDesc(UserMovieListItem::getAddedAt) : wrapper.orderByAsc(UserMovieListItem::getAddedAt);
            } else {
                wrapper = desc ? wrapper.orderByDesc(UserMovieListItem::getRating) : wrapper.orderByAsc(UserMovieListItem::getRating);
            }
            IPage<UserMovieListItem> itemPage = itemMapper.selectPage(page, wrapper);
            Page<UserListItemVO> voPage = new Page<>(pageNum, pageSize);
            voPage.setTotal(itemPage.getTotal());
            voPage.setRecords(enrichItems(itemPage.getRecords()));
            return voPage;
        }

        // 对于内容表字段的排序（year, douban），先查所有 items 再在 VO 层排序
        Page<UserMovieListItem> page = new Page<>(pageNum, pageSize);
        IPage<UserMovieListItem> itemPage = itemMapper.selectPage(page, new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId));

        List<UserListItemVO> voList = enrichItems(itemPage.getRecords());

        // 在 VO 层排序
        voList.sort((a, b) -> {
            int cmp = 0;
            switch (sort) {
                case "year":
                    int ya = a.getYear() != null ? a.getYear() : 0;
                    int yb = b.getYear() != null ? b.getYear() : 0;
                    cmp = Integer.compare(ya, yb);
                    break;
                case "douban":
                    double da = a.getRating() != null ? a.getRating().doubleValue() : 0;
                    double db = b.getRating() != null ? b.getRating().doubleValue() : 0;
                    cmp = Double.compare(da, db);
                    break;
                default:
                    cmp = 0;
            }
            return desc ? -cmp : cmp;
        });

        Page<UserListItemVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setTotal(itemPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
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
            movieMap = movieMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Movie::getId, Function.identity(), (a, b) -> a));
        }
        if (idsByType.containsKey("drama")) {
            List<Long> ids = idsByType.get("drama");
            dramaMap = dramaMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Drama::getId, Function.identity(), (a, b) -> a));
        }
        if (idsByType.containsKey("variety")) {
            List<Long> ids = idsByType.get("variety");
            varietyMap = varietyMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Variety::getId, Function.identity(), (a, b) -> a));
        }
        if (idsByType.containsKey("anime")) {
            List<Long> ids = idsByType.get("anime");
            animeMap = animeMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Anime::getId, Function.identity(), (a, b) -> a));
        }
        if (idsByType.containsKey("short_drama")) {
            List<Long> ids = idsByType.get("short_drama");
            shortDramaMap = shortDramaMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(ShortDrama::getId, Function.identity(), (a, b) -> a));
        }

        // 组装 VO
        List<UserListItemVO> result = new ArrayList<>(items.size());
        for (UserMovieListItem item : items) {
            result.add(enrichItem(item, movieMap, dramaMap, varietyMap, animeMap, shortDramaMap));
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
        vo.setAddedAt(item.getAddedAt());
        vo.setUserRating(item.getRating());
        vo.setNote(item.getNote());

        String ct = item.getContentType();
        Long movieId = item.getMovieId();

        if ("movie".equals(ct)) {
            Movie m = movieMap.get(movieId);
            if (m != null) {
                vo.setTitle(m.getTitle());
                vo.setCover(m.getPosterUrl());
                vo.setYear(m.getYear());
                vo.setRating(m.getScoreDouban());
                vo.setRegion(m.getRegion());
                vo.setGenre(m.getGenre());
                vo.setDirector(m.getDirector());
                vo.setActor(m.getActor());
                vo.setDuration(m.getDuration());
            }
        } else if ("drama".equals(ct)) {
            Drama d = dramaMap.get(movieId);
            if (d != null) {
                vo.setTitle(d.getTitle());
                vo.setCover(d.getPosterUrl());
                vo.setYear(d.getYear());
                vo.setRating(d.getScoreDouban());
                vo.setRegion(d.getRegion());
                vo.setGenre(d.getGenre());
                vo.setDirector(d.getDirector());
                vo.setActor(d.getActor());
                vo.setTotalEpisode(d.getTotalEpisode());
            }
        } else if ("variety".equals(ct)) {
            Variety v = varietyMap.get(movieId);
            if (v != null) {
                vo.setTitle(v.getTitle());
                vo.setCover(v.getPosterUrl());
                vo.setYear(v.getYear());
                vo.setRating(v.getScoreDouban());
                vo.setRegion(v.getRegion());
                vo.setGenre(v.getGenre());
                vo.setDirector(v.getDirector());
                vo.setActor(v.getActor());
                vo.setTotalEpisode(v.getTotalEpisode());
            }
        } else if ("anime".equals(ct)) {
            Anime a = animeMap.get(movieId);
            if (a != null) {
                vo.setTitle(a.getTitle());
                vo.setCover(a.getPosterUrl());
                vo.setYear(a.getYear());
                vo.setRating(a.getScoreDouban());
                vo.setRegion(a.getRegion());
                vo.setGenre(a.getGenre());
                vo.setDirector(a.getDirector());
                vo.setActor(a.getActor());
                vo.setTotalEpisode(a.getTotalEpisode());
            }
        } else if ("short_drama".equals(ct)) {
            ShortDrama s = shortDramaMap.get(movieId);
            if (s != null) {
                vo.setTitle(s.getTitle());
                vo.setCover(s.getPosterUrl());
                vo.setYear(s.getYear());
                vo.setRegion(s.getRegion());
                vo.setGenre(s.getGenre());
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

        UserMovieListItem existing = itemMapper.selectOne(new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId)
                .eq(UserMovieListItem::getMovieId, movieId)
                .eq(UserMovieListItem::getContentType, contentType));
        if (existing == null) {
            throw new RuntimeException("条目不存在");
        }
        if (rating != null) existing.setRating(rating);
        if (note != null) existing.setNote(note);
        itemMapper.updateById(existing);
    }

    @Override
    public List<Map<String, Object>> getMovieStatus(Long userId, Long movieId, String contentType) {
        return getMovieStatusInternal(userId, movieId, contentType, null);
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
                itemMapper.delete(new LambdaQueryWrapper<UserMovieListItem>()
                        .eq(UserMovieListItem::getListId, listId)
                        .eq(UserMovieListItem::getMovieId, movieId)
                        .eq(UserMovieListItem::getContentType, contentType));
            }
        }
    }

    @Override
    public Map<Long, List<Map<String, Object>>> getMovieStatusBatch(Long userId, List<Long> movieIds, String contentType) {
        // 只查一次用户片单，共享给所有 movieId
        List<UserMovieList> lists = getUserLists(userId);
        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        for (Long movieId : movieIds) {
            result.put(movieId, getMovieStatusInternal(userId, movieId, contentType, lists));
        }
        return result;
    }
}
