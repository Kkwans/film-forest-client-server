package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.content.entity.UserMovieList;
import com.filmforest.content.entity.UserMovieListItem;
import com.filmforest.content.mapper.UserMovieListItemMapper;
import com.filmforest.content.mapper.UserMovieListMapper;
import com.filmforest.content.service.impl.UserMovieListServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMovieListPagingTest {

    @Mock private UserMovieListMapper listMapper;
    @Mock private UserMovieListItemMapper itemMapper;
    private UserMovieListServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "user-list-paging-test"),
                UserMovieListItem.class);
        service = new UserMovieListServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", listMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);

        UserMovieList list = new UserMovieList();
        list.setId(9L);
        list.setUserId(42L);
        when(listMapper.selectById(9L)).thenReturn(list);
    }

    @Test
    void appliesContentTypeBeforeDatabasePagination() {
        Page<UserMovieListItem> emptyPage = new Page<>(1, 20);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        service.getListItems(42L, 9L, 1, 20, "addedAt", "desc", "movie");

        ArgumentCaptor<LambdaQueryWrapper<UserMovieListItem>> wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(itemMapper).selectPage(any(Page.class), wrapper.capture());
        wrapper.getValue().getSqlSegment();
        assertThat(wrapper.getValue().getParamNameValuePairs().values()).contains(9L, "movie");
    }

    @Test
    void sortsCompleteFilteredSetBeforePagingForContentFields() {
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        var result = service.getListItems(42L, 9L, 2, 20, "year", "desc", "anime");

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getTotal()).isZero();
        verify(itemMapper).selectList(any(Wrapper.class));
        verify(itemMapper, never()).selectPage(any(Page.class), any(Wrapper.class));
    }
}
