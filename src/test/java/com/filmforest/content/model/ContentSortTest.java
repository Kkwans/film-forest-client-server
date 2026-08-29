package com.filmforest.content.model;

import com.filmforest.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentSortTest {

    @Test
    void blankSortDefaultsToLatest() {
        assertThat(ContentSort.parse(null, ContentType.MOVIE)).isEqualTo(ContentSort.LATEST);
        assertThat(ContentSort.parse("  ", ContentType.DRAMA)).isEqualTo(ContentSort.LATEST);
    }

    @Test
    void acceptsMovieRtAndCanonicalSorts() {
        assertThat(ContentSort.parse("rt", ContentType.MOVIE)).isEqualTo(ContentSort.RT);
        assertThat(ContentSort.parse("DouBan", ContentType.MOVIE)).isEqualTo(ContentSort.DOUBAN);
        assertThat(ContentSort.parse("imdb", ContentType.ANIME)).isEqualTo(ContentSort.IMDB);
    }

    @Test
    void rejectsRtForNonMovie() {
        assertThatThrownBy(() -> ContentSort.parse("rt", ContentType.DRAMA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持排序方式: rt");
    }

    @Test
    void rejectsUnknownSort() {
        assertThatThrownBy(() -> ContentSort.parse("popularity", ContentType.MOVIE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的排序");
    }
}
