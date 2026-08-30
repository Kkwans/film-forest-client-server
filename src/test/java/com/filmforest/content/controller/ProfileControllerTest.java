package com.filmforest.content.controller;

import com.filmforest.content.dto.ProfileOverviewView;
import com.filmforest.content.service.ProfileOverviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileControllerTest {

    @Test
    void requiresAuthenticatedUser() {
        ProfileOverviewService service = mock(ProfileOverviewService.class);
        ProfileController controller = new ProfileController(service);
        HttpServletRequest request = mock(HttpServletRequest.class);

        var result = controller.overview(request);

        assertThat(result.getCode()).isEqualTo(401);
    }

    @Test
    void returnsOverviewForAuthenticatedUser() {
        ProfileOverviewService service = mock(ProfileOverviewService.class);
        ProfileController controller = new ProfileController(service);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ProfileOverviewView overview = new ProfileOverviewView(
                new ProfileOverviewView.Stats(3, 1, 2, 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(request.getAttribute("userId")).thenReturn(42L);
        when(service.getOverview(42L)).thenReturn(overview);

        var result = controller.overview(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(overview);
        verify(service).getOverview(42L);
    }
}
