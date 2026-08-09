package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.GenreOption;
import com.filmforest.content.entity.Tag;
import com.filmforest.content.service.TagService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagControllerTest {

    @Test
    void standardGenreEndpointReturnsServiceCatalog() {
        TagService service = mock(TagService.class);
        TagController controller = new TagController(service);
        Tag drama = new Tag();
        drama.setId(1L);
        drama.setCode("drama");
        drama.setName("剧情");
        when(service.getStandardGenres("movie")).thenReturn(List.of(drama));

        Result<List<GenreOption>> result = controller.getStandardGenres("movie");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(new GenreOption(1L, "drama", "剧情", null));
        verify(service).getStandardGenres("movie");
    }
}
