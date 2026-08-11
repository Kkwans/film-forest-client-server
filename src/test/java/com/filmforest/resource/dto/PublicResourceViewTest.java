package com.filmforest.resource.dto;

import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceOnline;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicResourceViewTest {

    @Test
    void cloudProjectionKeepsActionUrlAndDiskType() {
        ResourceCloud cloud = new ResourceCloud();
        cloud.setId(7L);
        cloud.setTitle("示例资源");
        cloud.setUrl("https://pan.quark.cn/s/example");
        cloud.setPassword("ab12");
        cloud.setDiskType("quark");

        PublicCloudResource view = PublicCloudResource.from(cloud);

        assertThat(view.url()).isEqualTo("https://pan.quark.cn/s/example");
        assertThat(view.extractionCode()).isEqualTo("ab12");
        assertThat(view.diskType()).isEqualTo("quark");
    }

    @Test
    void magnetQualityCategoriesAreMutuallyExclusive() {
        assertThat(view("Movie.1080p.WEB-DL", "1080P", false).qualityCategory())
                .isEqualTo("1080p");
        assertThat(view("电影.1080p.中文字幕", "1080P", false).qualityCategory())
                .isEqualTo("中字1080p");
        assertThat(view("电影.1080p.特效字幕", "1080P", false).qualityCategory())
                .isEqualTo("特效1080p");
        assertThat(view("没有清晰度标识", null, false).qualityCategory())
                .isEqualTo("未知");
    }

    @Test
    void storedSubtitleFlagStillProducesChineseSubtitleCategory() {
        assertThat(view("Movie.2160p.WEB-DL", "4K", true).qualityCategory())
                .isEqualTo("中字4K");
    }

    @Test
    void onlineProjectionKeepsPlaybackAndFallbackUrlsSeparate() {
        ResourceOnline online = new ResourceOnline();
        online.setId(9L);
        online.setSourceName("天堂 · HD中字");
        online.setSourceUrl("https://cdn.example.test/movie.m3u8");
        online.setSourcePageUrl("https://www.pkmp4.xyz/py/42-1-1.html");
        online.setPlaybackType("HLS");

        PublicOnlineResource view = PublicOnlineResource.from(online);

        assertThat(view.sourceUrl()).isEqualTo("https://cdn.example.test/movie.m3u8");
        assertThat(view.sourcePageUrl()).isEqualTo("https://www.pkmp4.xyz/py/42-1-1.html");
        assertThat(view.playbackType()).isEqualTo("HLS");
    }

    private static PublicMagnetResource view(String title, String resolution, boolean subtitle) {
        ResourceMagnet magnet = new ResourceMagnet();
        magnet.setTitle(title);
        magnet.setResolution(resolution);
        magnet.setHasSubtitle(subtitle);
        magnet.setIsSpecialSub(false);
        return PublicMagnetResource.from(magnet);
    }
}
