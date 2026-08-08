package com.filmforest.content.controller;

import com.filmforest.content.model.ContentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchControllerContractTest {

    @Test
    void typeFilterSupportsCanonicalAndRouteAliases() {
        assertThat(SearchController.parseTypeFilter("movie,short"))
                .containsExactlyInAnyOrder(ContentType.MOVIE, ContentType.SHORT_DRAMA);
        assertThat(SearchController.parseTypeFilter("all"))
                .containsExactlyInAnyOrder(ContentType.values());
    }
}
