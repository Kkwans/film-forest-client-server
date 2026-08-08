package com.filmforest.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.filmforest.content.entity.PasswordAlgorithm;
import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.mapper.UserMapper;
import com.filmforest.content.service.PasswordService;
import com.filmforest.content.service.UserMovieListService;
import com.filmforest.content.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 用户服务实现
 * 提供用户注册、登录和信息查询
 */
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMovieListService userMovieListService;
    private final PasswordService passwordService;

    public UserServiceImpl(UserMovieListService userMovieListService, PasswordService passwordService) {
        this.userMovieListService = userMovieListService;
        this.passwordService = passwordService;
    }

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
        user.setPasswordHash(passwordService.encode(password));
        user.setPasswordAlgorithm(PasswordAlgorithm.BCRYPT);
        user.setMustChangePassword(false);
        user.setEmail(email);
        user.setNickname(username); // 默认昵称为用户名
        user.setStatus(1); // 正常状态
        user.setRole(UserRole.USER);
        save(user);

        // 创建默认片单
        userMovieListService.createDefaultLists(user.getId());

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User login(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        PasswordService.Verification verification = passwordService.verify(
                password, user.getPasswordHash(), user.getPasswordAlgorithm());
        if (!verification.matches()) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用");
        }

        if (verification.needsUpgrade()) {
            user.setPasswordHash(passwordService.encode(password));
            user.setPasswordAlgorithm(PasswordAlgorithm.BCRYPT);
            updateById(user);
        }

        return user;
    }

    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
}
