package com.filmforest.content.model;

import com.filmforest.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentTypeTest {

    @Test
    void parsesCanonicalCodesAndShortAliases() {
        assertThat(ContentType.parse("movie")).isEqualTo(ContentType.MOVIE);
        assertThat(ContentType.parse(" short ")).isEqualTo(ContentType.SHORT_DRAMA);
        assertThat(ContentType.parse("short-drama")).isEqualTo(ContentType.SHORT_DRAMA);
        assertThat(ContentType.SHORT_DRAMA.routeSegment()).isEqualTo("short");
        assertThat(ContentType.SHORT_DRAMA.collectionPath()).isEqualTo("/api/short-dramas");
    }

    @Test
    void rejectsUnknownOrBlankTypes() {
        assertThatThrownBy(() -> ContentType.parse("documentary"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的内容类型");
        assertThatThrownBy(() -> ContentType.parse(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("内容类型不能为空");
    }
}
