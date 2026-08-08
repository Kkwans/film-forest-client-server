package com.filmforest.content.poster.tmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbApiClientSpringWiringTest {

    @Test
    void springSelectsTheProductionConstructorWhenTestConstructorAlsoExists() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
            context.register(TmdbApiClient.class);
            context.refresh();

            assertThat(context.getBean(TmdbApiClient.class)).isNotNull();
        }
    }
}
