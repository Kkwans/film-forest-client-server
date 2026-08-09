package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.content.model.ContentType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/** 将“是否存在公开可用资源”条件统一应用到五类内容目录查询。 */
@Service
public class ContentResourceFilter {

    private static final Map<ContentType, String> CONTENT_TABLES = contentTables();

    public <T> void apply(LambdaQueryWrapper<T> wrapper, ContentType contentType, Boolean hasResource) {
        if (hasResource == null) {
            return;
        }

        String contentTable = CONTENT_TABLES.get(contentType);
        String online = activeResourceExists("resource_online", contentType.code(), contentTable);
        String magnet = activeResourceExists("resource_magnet", contentType.code(), contentTable);
        String cloud = activeResourceExists("resource_cloud", contentType.code(), contentTable);

        if (hasResource) {
            wrapper.and(resources -> resources.exists(online).or().exists(magnet).or().exists(cloud));
        } else {
            wrapper.notExists(online).notExists(magnet).notExists(cloud);
        }
    }

    private static String activeResourceExists(String resourceTable, String contentType, String contentTable) {
        return "SELECT 1 FROM " + resourceTable + " resource"
                + " WHERE resource.content_type = '" + contentType + "'"
                + " AND resource.content_id = " + contentTable + ".id"
                + " AND resource.is_deleted = 0"
                + " AND resource.enabled = 1"
                + " AND resource.removed_at IS NULL";
    }

    private static Map<ContentType, String> contentTables() {
        EnumMap<ContentType, String> tables = new EnumMap<>(ContentType.class);
        tables.put(ContentType.MOVIE, "movie");
        tables.put(ContentType.DRAMA, "drama");
        tables.put(ContentType.VARIETY, "variety");
        tables.put(ContentType.ANIME, "anime");
        tables.put(ContentType.SHORT_DRAMA, "short_drama");
        return Map.copyOf(tables);
    }
}
