package com.filmforest.content.service.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendServiceImplTest {

    @Test
    void latestSectionExcludesItemsAlreadyShownInHotSection() {
        List<Map<String, Object>> hot = List.of(item(1L), item(2L));
        List<Map<String, Object>> candidates = List.of(item(2L), item(3L), item(4L), item(5L));

        List<Map<String, Object>> latest = RecommendServiceImpl.withoutDuplicateIds(candidates, hot, 2);

        assertThat(latest).extracting(row -> row.get("id")).containsExactly(3L, 4L);
    }

    private Map<String, Object> item(Long id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        return item;
    }
}
