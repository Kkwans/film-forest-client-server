package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.User;
import com.filmforest.content.mapper.UserMapper;
import com.filmforest.content.service.UserMovieListService;
import com.filmforest.content.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import cn.hutool.crypto.digest.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMovieListService userMovieListService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(String username, String password, String email) {
        // 检查用户名是否已存在
        User existing = findByUsername(username);
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 创建用户
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setEmail(email);
        user.setNickname(username); // 默认昵称为用户名
        user.setStatus(1); // 正常状态
        save(user);

        // 创建默认片单
        userMovieListService.createDefaultLists(user.getId());

        return user;
    }

    @Override
    public User login(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        return user;
    }

    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
}
