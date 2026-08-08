package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.dto.PosterSettingView;
import com.filmforest.content.entity.PosterEnrichmentJob;
import com.filmforest.content.mapper.PosterEnrichmentJobMapper;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.PosterBatchContentSource;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PosterEnrichmentJobServiceTest {

    private final PosterEnrichmentJobMapper mapper = mock(PosterEnrichmentJobMapper.class);
    private final UserPosterSettingService settingService = mock(UserPosterSettingService.class);
    private final PosterBatchContentSource contentSource = mock(PosterBatchContentSource.class);
    private final PosterEnrichmentJobWorker worker = mock(PosterEnrichmentJobWorker.class);
    private final PosterEnrichmentJobService service =
            new PosterEnrichmentJobService(mapper, settingService, contentSource, worker);

    @BeforeEach
    void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "poster-job-service-test"),
                PosterEnrichmentJob.class);
    }

    @Test
    void explicitStartPersistsScopedJobAndDispatchesWorker() {
        when(settingService.get(7L)).thenReturn(setting("tmdb", true));
        when(contentSource.count(ContentType.MOVIE)).thenReturn(3L);
        doAnswer(invocation -> {
            PosterEnrichmentJob job = invocation.getArgument(0);
            job.setId(41L);
            return 1;
        }).when(mapper).insert(any(PosterEnrichmentJob.class));

        var result = service.start(7L, "movie");

        assertThat(result.id()).isEqualTo(41L);
        assertThat(result.status()).isEqualTo("queued");
        assertThat(result.totalCount()).isEqualTo(3);
        verify(worker).run(41L);
    }

    @Test
    void startRequiresTmdbPreferenceAndConfiguredCredential() {
        when(settingService.get(7L)).thenReturn(setting("original", true));

        assertThatThrownBy(() -> service.start(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TMDB");
    }

    private PosterSettingView setting(String source, boolean configured) {
        return new PosterSettingView(source, configured, configured ? "api_key" : null,
                configured ? "••••1234" : null, configured ? "valid" : "not_configured", null, null);
    }
}
