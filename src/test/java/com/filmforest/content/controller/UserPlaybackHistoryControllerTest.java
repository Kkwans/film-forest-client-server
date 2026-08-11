package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.UserPlaybackHistoryRequest;
import com.filmforest.content.dto.UserPlaybackHistoryView;
import com.filmforest.content.service.UserPlaybackHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPlaybackHistoryControllerTest {

    @Test
    void missingAuthenticationIsRejected() {
        UserPlaybackHistoryService service = mock(UserPlaybackHistoryService.class);
        UserPlaybackHistoryController controller = new UserPlaybackHistoryController(service);

        Result<?> result = controller.list(new MockHttpServletRequest(), 20);

        assertThat(result.getCode()).isEqualTo(401);
        verify(service, never()).list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void listHonorsLimitAndUserAttribute() {
        UserPlaybackHistoryService service = mock(UserPlaybackHistoryService.class);
        UserPlaybackHistoryController controller = new UserPlaybackHistoryController(service);
        MockHttpServletRequest request = authenticatedRequest(42L);
        UserPlaybackHistoryView view = new UserPlaybackHistoryView();
        view.setContentId(7L);
        when(service.list(42L, 3)).thenReturn(List.of(view));

        Result<List<UserPlaybackHistoryView>> result = controller.list(request, 3);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(view);
        verify(service).list(42L, 3);
    }

    @Test
    void limitOutsideContractIsRejected() {
        UserPlaybackHistoryService service = mock(UserPlaybackHistoryService.class);
        UserPlaybackHistoryController controller = new UserPlaybackHistoryController(service);

        Result<?> result = controller.list(authenticatedRequest(42L), 101);

        assertThat(result.getCode()).isEqualTo(400);
        verify(service, never()).list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void upsertRemoveAndClearUseAuthenticatedUser() {
        UserPlaybackHistoryService service = mock(UserPlaybackHistoryService.class);
        UserPlaybackHistoryController controller = new UserPlaybackHistoryController(service);
        MockHttpServletRequest request = authenticatedRequest(42L);
        UserPlaybackHistoryRequest body = new UserPlaybackHistoryRequest();

        Result<?> upsert = controller.upsert(request, body);
        Result<?> remove = controller.remove(request, "short", 7L);
        Result<?> clear = controller.clear(request);

        assertThat(upsert.getCode()).isEqualTo(200);
        assertThat(remove.getCode()).isEqualTo(200);
        assertThat(clear.getCode()).isEqualTo(200);
        verify(service).upsert(42L, body);
        verify(service).remove(42L, "short", 7L);
        verify(service).clear(42L);
    }

    private MockHttpServletRequest authenticatedRequest(long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        return request;
    }
}
