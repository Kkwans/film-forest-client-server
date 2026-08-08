package com.filmforest.content.filter;

import com.filmforest.content.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtUtil.class));

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

    private boolean isPublic(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        return filter.isPublicRequest(request);
    }
}
