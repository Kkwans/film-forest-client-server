package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.UserMovieList;
import com.filmforest.content.entity.UserMovieListItem;
import com.filmforest.content.mapper.UserMovieListItemMapper;
import com.filmforest.content.mapper.UserMovieListMapper;
import com.filmforest.content.service.UserMovieListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserMovieListServiceImpl extends ServiceImpl<UserMovieListMapper, UserMovieList> implements UserMovieListService {

    @Autowired
    private UserMovieListItemMapper itemMapper;

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
        return list(new LambdaQueryWrapper<UserMovieList>()
                .eq(UserMovieList::getUserId, userId)
                .orderByAsc(UserMovieList::getIsDefault)
                .orderByDesc(UserMovieList::getCreatedAt));
    }

    @Override
    public UserMovieList createList(Long userId, String name, String description) {
        UserMovieList list = new UserMovieList();
        list.setUserId(userId);
        list.setName(name);
        list.setType("custom");
        list.setDescription(description);
        list.setIsDefault(0);
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
    public void addItem(Long userId, Long listId, Long movieId, String contentType) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }

        // 检查是否已存在
        Long count = itemMapper.selectCount(new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId)
                .eq(UserMovieListItem::getMovieId, movieId)
                .eq(UserMovieListItem::getContentType, contentType));
        if (count > 0) {
            throw new RuntimeException("该影视已在片单中");
        }

        UserMovieListItem item = new UserMovieListItem();
        item.setListId(listId);
        item.setMovieId(movieId);
        item.setContentType(contentType);
        itemMapper.insert(item);
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
    public IPage<UserMovieListItem> getListItems(Long userId, Long listId, int pageNum, int pageSize) {
        // 校验片单归属
        UserMovieList list = getById(listId);
        if (list == null || !list.getUserId().equals(userId)) {
            throw new RuntimeException("片单不存在");
        }

        Page<UserMovieListItem> page = new Page<>(pageNum, pageSize);
        return itemMapper.selectPage(page, new LambdaQueryWrapper<UserMovieListItem>()
                .eq(UserMovieListItem::getListId, listId)
                .orderByDesc(UserMovieListItem::getAddedAt));
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
