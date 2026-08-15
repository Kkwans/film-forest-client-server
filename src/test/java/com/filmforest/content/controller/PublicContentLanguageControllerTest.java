package com.filmforest.content.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.filmforest.common.dto.Result;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.service.ContentDetailEnrichmentService;
import com.filmforest.content.service.MovieService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicContentLanguageControllerTest {

    @Test
    void movieListForwardsLanguageWithoutChangingExistingPagingArguments() {
        MovieService service = mock(MovieService.class);
        IPage<Movie> page = mock(IPage.class);
        when(service.pageList(2, 10, 2024, "中国", "剧情", "score", 2020, 2024,
                7L, true, "asc", "zh-CN")).thenReturn(page);

        Result<?> result = new MovieController(service, mock(ContentDetailEnrichmentService.class)).list(
                2, 10, 2024, "中国", "剧情", "zh-CN", "score", 2020, 2024,
                7L, true, "asc");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(page);
        verify(service).pageList(2, 10, 2024, "中国", "剧情", "score", 2020, 2024,
                7L, true, "asc", "zh-CN");
    }
}
