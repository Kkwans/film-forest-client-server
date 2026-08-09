package com.filmforest.content.service.impl;

import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.entity.PasswordAlgorithm;
import com.filmforest.content.entity.User;
import com.filmforest.content.mapper.UserMapper;
import com.filmforest.content.service.PasswordService;
import com.filmforest.content.service.UserMovieListService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplPasswordTest {

    private final UserMovieListService listService = mock(UserMovieListService.class);
    private final PasswordService passwordService = mock(PasswordService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserServiceImpl service = service();

    @Test
    void changesTemporaryPasswordAndClearsRequiredFlag() {
        User user = activeUser();
        when(userMapper.selectById(7L)).thenReturn(user);
        when(passwordService.verify("temporary", "old-hash", PasswordAlgorithm.BCRYPT))
                .thenReturn(new PasswordService.Verification(true, false));
        when(passwordService.encode("new-secret12")).thenReturn("new-hash");

        service.changePassword(7L, "temporary", "new-secret12");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordAlgorithm()).isEqualTo(PasswordAlgorithm.BCRYPT);
        assertThat(user.getMustChangePassword()).isFalse();
        verify(userMapper).updateById(user);
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutWriting() {
        User user = activeUser();
        when(userMapper.selectById(7L)).thenReturn(user);
        when(passwordService.verify("wrong", "old-hash", PasswordAlgorithm.BCRYPT))
                .thenReturn(new PasswordService.Verification(false, false));

        assertThatThrownBy(() -> service.changePassword(7L, "wrong", "new-secret12"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前密码不正确");
    }

    private UserServiceImpl service() {
        UserServiceImpl target = new UserServiceImpl(listService, passwordService);
        ReflectionTestUtils.setField(target, "baseMapper", userMapper);
        return target;
    }

    private User activeUser() {
        User user = new User();
        user.setId(7L);
        user.setStatus(1);
        user.setPasswordHash("old-hash");
        user.setPasswordAlgorithm(PasswordAlgorithm.BCRYPT);
        user.setMustChangePassword(true);
        return user;
    }
}
