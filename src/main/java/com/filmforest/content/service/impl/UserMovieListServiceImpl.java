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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
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

        // 删除片单下的所有条目
        itemMapper.delete(new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId));
        // 删除片单
        removeById(listId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, Long listId, Long movieId, String contentType, BigDecimal rating, String note) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }

        // 检查是否已存在
        UserMovieListItem existing = itemMapper.selectOne(new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId)
                .eq(UserMovieListItem::getMovieId, movieId)
                .eq(UserMovieListItem::getContentType, contentType));
        if (existing != null) {
            // 已存在则更新评分和备注
            if (rating != null) existing.setRating(rating);
            if (note != null) existing.setNote(note);
            itemMapper.updateById(existing);
            return;
        }

        UserMovieListItem item = new UserMovieListItem();
        item.setListId(listId);
        item.setMovieId(movieId);
        item.setContentType(contentType);
        item.setRating(rating);
        item.setNote(note);
        itemMapper.insert(item);

        // 如果添加到在看或看过片单，自动从想看片单中删除
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
    public IPage<UserListItemVO> getListItems(Long userId, Long listId, int pageNum, int pageSize) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }

        Page<UserMovieListItem> page = new Page<>(pageNum, pageSize);
        IPage<UserMovieListItem> itemPage = itemMapper.selectPage(page, new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId)
                .orderByDesc(UserMovieListItem::getAddedAt));

        // 转换为 VO，填充影视信息
        Page<UserListItemVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setTotal(itemPage.getTotal());
        voPage.setRecords(itemPage.getRecords().stream()
                .map(this::enrichItem)
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 为片单条目填充影视基本信息
     */
    private UserListItemVO enrichItem(UserMovieListItem item) {
        UserListItemVO vo = new UserListItemVO();
        vo.setId(item.getId());
        vo.setListId(item.getListId());
        vo.setMovieId(item.getMovieId());
        vo.setContentType(item.getContentType());
        vo.setAddedAt(item.getAddedAt());
        vo.setUserRating(item.getRating());
        vo.setNote(item.getNote());

        // 根据 contentType 查询对应的表
        String ct = item.getContentType();
        Long movieId = item.getMovieId();

        if ("movie".equals(ct)) {
            Movie m = movieMapper.selectById(movieId);
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
            Drama d = dramaMapper.selectById(movieId);
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
            Variety v = varietyMapper.selectById(movieId);
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
            Anime a = animeMapper.selectById(movieId);
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
            ShortDrama s = shortDramaMapper.selectById(movieId);
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
        // 获取用户所有片单
        List<UserMovieList> lists = getUserLists(userId);
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

        return lists.stream().map(list -> {
            Map<String, Object> map = new HashMap<>();
            map.put("listId", list.getId());
            map.put("listName", list.getName());
            map.put("type", list.getType());
            map.put("added", matchedListIds.contains(list.getId()));
            return map;
        }).collect(Collectors.toList());
    }
}
