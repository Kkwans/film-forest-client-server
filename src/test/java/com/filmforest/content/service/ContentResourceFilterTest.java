package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.model.ContentType;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentResourceFilterTest {

    private final ContentResourceFilter filter = new ContentResourceFilter();

    @BeforeAll
    static void initializeMovieMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), Movie.class.getName());
        assistant.setCurrentNamespace(Movie.class.getName());
        TableInfoHelper.initTableInfo(assistant, Movie.class);
    }

    @Test
    void availableFilterMatchesAnyActivePublicResourceKind() {
        LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();

        filter.apply(wrapper, ContentType.MOVIE, true);

        assertThat(wrapper.getSqlSegment())
                .contains("resource_online", "resource_magnet", "resource_cloud")
                .contains("resource.content_type = 'movie'")
                .contains("resource.content_id = movie.id")
                .contains("resource.is_deleted = 0")
                .contains("resource.enabled = 1")
                .contains("resource.removed_at IS NULL")
                .contains("OR");
    }

    @Test
    void unavailableFilterExcludesEveryActivePublicResourceKind() {
        LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();

        filter.apply(wrapper, ContentType.MOVIE, false);

        assertThat(wrapper.getSqlSegment())
                .contains("NOT EXISTS")
                .doesNotContain(" OR ");
    }

    @Test
    void omittedFilterDoesNotChangeQuery() {
        LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();

        filter.apply(wrapper, ContentType.MOVIE, null);

        assertThat(wrapper.getSqlSegment()).isEmpty();
    }
}
