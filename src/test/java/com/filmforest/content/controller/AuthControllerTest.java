package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.dto.LoginRequest;
import com.filmforest.content.service.LoginAttemptService;
import com.filmforest.content.service.UserService;
import com.filmforest.content.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final UserService userService = mock(UserService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuthController controller = new AuthController(userService, jwtUtil, attempts);

    @Test
    void rejectsBlockedLoginBeforePasswordVerification() {
        LoginRequest request = loginRequest();
        when(attempts.isBlocked("192.0.2.10", "admin")).thenReturn(true);

        Result<?> result = controller.login(request, servletRequest());

        assertThat(result.getCode()).isEqualTo(429);
        verify(userService, never()).login("admin", "secret12");
    }

    @Test
    void recordsOnlyExpectedAuthenticationFailure() {
        LoginRequest request = loginRequest();
        when(userService.login("admin", "secret12"))
                .thenThrow(new BusinessException("用户名或密码错误"));

        Result<?> result = controller.login(request, servletRequest());

        assertThat(result.getCode()).isEqualTo(400);
        verify(attempts).recordFailure("192.0.2.10", "admin");
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("secret12");
        return request;
    }

    private MockHttpServletRequest servletRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
