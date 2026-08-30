package com.filmforest.content.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserMovieListItemMapperXmlTest {

    private MybatisConfiguration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new MybatisConfiguration();
        try (InputStream input = getClass().getResourceAsStream(
                "/mapper/UserMovieListItemMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/UserMovieListItemMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void registersCountAndPageStatements() {
        assertThat(configuration.hasStatement(
                "com.filmforest.content.mapper.UserMovieListItemMapper.countVisible")).isTrue();
        assertThat(configuration.hasStatement(
                "com.filmforest.content.mapper.UserMovieListItemMapper.selectVisiblePage")).isTrue();
    }

    @Test
    void appliesSafeSortAndContentTypeBranchInBoundSql() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("listId", 9L);
        parameters.put("contentType", "anime");
        parameters.put("sort", "year");
        parameters.put("desc", true);
        parameters.put("limit", 20);
        parameters.put("offset", 40L);

        MappedStatement statement = configuration.getMappedStatement(
                "com.filmforest.content.mapper.UserMovieListItemMapper.selectVisiblePage");
        BoundSql boundSql = statement.getBoundSql(parameters);
        String sql = boundSql.getSql().replaceAll("\\s+", " ");

        assertThat(sql).contains("JOIN anime c");
        assertThat(sql).doesNotContain("JOIN movie c");
        assertThat(sql).contains("ORDER BY visible.year DESC");
        assertThat(sql).contains("visible.id DESC");
        assertThat(sql).contains("LIMIT ? OFFSET ?");
    }
}
