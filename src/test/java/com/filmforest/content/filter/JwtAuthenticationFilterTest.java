package com.filmforest.content.filter;

import com.filmforest.content.entity.User;
import com.filmforest.content.entity.UserRole;
import com.filmforest.content.service.UserService;
import com.filmforest.content.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserService userService = mock(UserService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, userService);

    @Test
    void allowsOnlyExplicitPublicReads() {
        assertThat(isPublic("GET", "/api/movies")).isTrue();
        assertThat(isPublic("GET", "/api/animes/1")).isTrue();
        assertThat(isPublic("GET", "/api/tags/hot")).isTrue();
        assertThat(isPublic("GET", "/api/movie/1/related")).isTrue();
        assertThat(isPublic("GET", "/api/recommend")).isTrue();
    }

    @Test
    void rejectsWritesAndUserSpecificReadsFromPublicBoundary() {
        assertThat(isPublic("POST", "/api/movies")).isFalse();
        assertThat(isPublic("PUT", "/api/tags/content/movie/1")).isFalse();
        assertThat(isPublic("GET", "/api/recommend/personalized")).isFalse();
        assertThat(isPublic("GET", "/api/auth/me")).isFalse();
        assertThat(isPublic("POST", "/api/auth/register")).isFalse();
    }

    @Test
    void allowsProtectedRequestForCurrentActiveAccount() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        when(userService.getById(7L)).thenReturn(user(1, 0));
        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(request.getAttribute("userId")).isEqualTo(7L);
    }

    @Test
    void revokesDisabledAccountImmediately() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        when(userService.getById(7L)).thenReturn(user(0, 0));
        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    private boolean isPublic(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        return filter.isPublicRequest(request);
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer valid-token");
        return request;
    }

    private User user(int status, int deleted) {
        User user = new User();
        user.setId(7L);
        user.setUsername("family");
        user.setStatus(status);
        user.setDeleted(deleted);
        user.setRole(UserRole.USER);
        return user;
    }
}
