package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.GenreOption;
import com.filmforest.content.service.PublishedGenreQueryService;
import com.filmforest.content.service.PublishedTagQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagControllerTest {

    @Test
    void standardGenreEndpointReturnsServiceCatalog() {
        PublishedGenreQueryService genreQueryService = mock(PublishedGenreQueryService.class);
        PublishedTagQueryService tagQueryService = mock(PublishedTagQueryService.class);
        TagController controller = new TagController(genreQueryService, tagQueryService);
        GenreOption drama = new GenreOption(1L, "drama", "剧情", null, 3L);
        when(genreQueryService.listAvailable("movie")).thenReturn(List.of(drama));

        Result<List<GenreOption>> result = controller.getStandardGenres("movie");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(drama);
        verify(genreQueryService).listAvailable("movie");
    }
}
