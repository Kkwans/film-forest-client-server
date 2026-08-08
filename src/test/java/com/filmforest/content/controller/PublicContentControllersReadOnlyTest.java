package com.filmforest.content.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PublicContentControllersReadOnlyTest {

    private static final List<Class<?>> PUBLIC_CONTENT_CONTROLLERS = List.of(
            MovieController.class,
            DramaController.class,
            AnimeController.class,
            VarietyController.class,
            ShortDramaController.class,
            TagController.class
    );

    private static final Set<Class<? extends Annotation>> MUTATION_MAPPINGS = Set.of(
            PostMapping.class,
            PutMapping.class,
            PatchMapping.class,
            DeleteMapping.class
    );

    @Test
    void publicContentControllersMustNotExposeMutationHandlers() {
        List<String> mutationHandlers = PUBLIC_CONTENT_CONTROLLERS.stream()
                .flatMap(controller -> List.of(controller.getDeclaredMethods()).stream())
                .filter(PublicContentControllersReadOnlyTest::hasMutationMapping)
                .map(Method::toGenericString)
                .toList();

        assertThat(mutationHandlers)
                .as("客户端内容服务只能暴露只读查询，写入必须由管理服务承担")
                .isEmpty();
    }

    private static boolean hasMutationMapping(Method method) {
        return MUTATION_MAPPINGS.stream().anyMatch(method::isAnnotationPresent);
    }
}
