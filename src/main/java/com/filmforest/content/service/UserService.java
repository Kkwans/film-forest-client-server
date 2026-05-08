package com.filmforest.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.filmforest.content.entity.User;

public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param username 用户名
     * @param password 明文密码
     * @param email 邮箱（可选）
     * @return 注册成功的用户
     */
    User register(String username, String password, String email);

    /**
     * 用户登录
     * @param username 用户名
     * @param password 明文密码
     * @return 用户信息
     */
    User login(String username, String password);

    /**
     * 根据用户名查找用户
     */
    User findByUsername(String username);
}
