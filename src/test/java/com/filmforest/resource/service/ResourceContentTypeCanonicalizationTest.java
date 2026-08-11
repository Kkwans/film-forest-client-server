package com.filmforest.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.filmforest.common.exception.BusinessException;
import com.filmforest.resource.entity.ResourceCloud;
import com.filmforest.resource.entity.ResourceMagnet;
import com.filmforest.resource.entity.ResourceOnline;
import com.filmforest.resource.mapper.ResourceCloudMapper;
import com.filmforest.resource.mapper.ResourceMagnetMapper;
import com.filmforest.resource.mapper.ResourceOnlineMapper;
import com.filmforest.resource.service.impl.ResourceCloudServiceImpl;
import com.filmforest.resource.service.impl.ResourceMagnetServiceImpl;
import com.filmforest.resource.service.impl.ResourceOnlineServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResourceContentTypeCanonicalizationTest {

    @Test
    void onlineQueriesUseShortDramaStorageCodeForBothEntryPoints() {
        ResourceOnlineMapper mapper = mock(ResourceOnlineMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        ResourceOnlineServiceImpl service = new ResourceOnlineServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        service.listByContent("short", 7L);
        service.listByContentAndEpisode("short_drama", 7L, 1, 2);

        ArgumentCaptor<QueryWrapper<ResourceOnline>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper, org.mockito.Mockito.times(2)).selectList(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(wrapper ->
                assertCanonicalContentType(wrapper));
    }

    @Test
    void magnetAndCloudQueriesUseCanonicalStorageCode() {
        ResourceMagnetMapper magnetMapper = mock(ResourceMagnetMapper.class);
        when(magnetMapper.selectList(any())).thenReturn(List.of());
        ResourceMagnetServiceImpl magnetService = new ResourceMagnetServiceImpl();
        ReflectionTestUtils.setField(magnetService, "baseMapper", magnetMapper);
        magnetService.listByContent("short", 1L);
        ArgumentCaptor<QueryWrapper<ResourceMagnet>> magnetCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(magnetMapper).selectList(magnetCaptor.capture());
        assertCanonicalContentType(magnetCaptor.getValue());

        ResourceCloudMapper cloudMapper = mock(ResourceCloudMapper.class);
        when(cloudMapper.selectList(any())).thenReturn(List.of());
        ResourceCloudServiceImpl cloudService = new ResourceCloudServiceImpl();
        ReflectionTestUtils.setField(cloudService, "baseMapper", cloudMapper);
        cloudService.listByContent("short_drama", 1L);
        ArgumentCaptor<QueryWrapper<ResourceCloud>> cloudCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(cloudMapper).selectList(cloudCaptor.capture());
        assertCanonicalContentType(cloudCaptor.getValue());
    }

    @Test
    void invalidContentTypeIsRejectedInsteadOfQueryingResources() {
        ResourceMagnetMapper mapper = mock(ResourceMagnetMapper.class);
        ResourceMagnetServiceImpl service = new ResourceMagnetServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.listByContent("language", 1L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }

    private static void assertCanonicalContentType(QueryWrapper<?> wrapper) {
        // 触发 MyBatis-Plus 参数格式化后再读取参数映射。
        wrapper.getSqlSegment();
        assertThat(wrapper.getParamNameValuePairs()).containsValue("short_drama");
    }
}
