package com.filmforest.content.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void preservesDatabaseTotalAndCurrentPage() {
        Page<String> source = new Page<>(2, 10, 37);
        source.setRecords(List.of("row-11", "row-12"));

        PageResult<String> result = PageResult.from(source);

        assertThat(result.records()).containsExactly("row-11", "row-12");
        assertThat(result.total()).isEqualTo(37);
        assertThat(result.current()).isEqualTo(2);
        assertThat(result.pages()).isEqualTo(4);
    }
}
