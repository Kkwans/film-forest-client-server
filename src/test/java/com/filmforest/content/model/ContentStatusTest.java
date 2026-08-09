package com.filmforest.content.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentStatusTest {

    @Test
    void codesMatchPublicApiContract() {
        assertThat(ContentStatus.DRAFT.code()).isZero();
        assertThat(ContentStatus.PUBLISHED.code()).isEqualTo(1);
        assertThat(ContentStatus.OFFLINE.code()).isEqualTo(2);
    }
}
