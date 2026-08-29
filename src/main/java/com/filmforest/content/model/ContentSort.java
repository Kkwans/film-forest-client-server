package com.filmforest.content.model;

import com.filmforest.common.exception.BusinessException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Canonical sort contract shared by all public content list endpoints. */
public enum ContentSort {
    LATEST("latest"),
    YEAR("year"),
    DOUBAN("douban"),
    IMDB("imdb"),
    RT("rt");

    private static final Set<ContentSort> NON_MOVIE_SORTS =
            EnumSet.of(LATEST, YEAR, DOUBAN, IMDB);

    private final String code;

    ContentSort(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ContentSort parse(String raw, ContentType contentType) {
        if (raw == null || raw.isBlank()) {
            return LATEST;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        ContentSort sort = Arrays.stream(values())
                .filter(candidate -> candidate.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400,
                        "不支持的排序方式: " + raw));
        if (contentType != ContentType.MOVIE && !NON_MOVIE_SORTS.contains(sort)) {
            throw new BusinessException(400,
                    contentType.displayName() + "不支持排序方式: " + raw);
        }
        return sort;
    }
}
