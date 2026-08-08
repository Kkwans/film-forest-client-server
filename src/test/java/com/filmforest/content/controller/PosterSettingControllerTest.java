package com.filmforest.content.controller;

import com.filmforest.content.dto.PosterPreferenceRequest;
import com.filmforest.content.dto.TmdbCredentialRequest;
import com.filmforest.content.service.UserPosterSettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosterSettingControllerTest {

    @Mock private UserPosterSettingService service;
    @Mock private HttpServletRequest request;

    private PosterSettingController controller;

    @BeforeEach
    void setUp() {
        controller = new PosterSettingController(service);
        when(request.getAttribute("userId")).thenReturn(17L);
    }

    @Test
    void everyMutationUsesAuthenticatedRequestUserId() {
        controller.savePreference(new PosterPreferenceRequest("tmdb"), request);
        controller.saveCredential(new TmdbCredentialRequest("api_key", "test-credential-1234"), request);
        controller.clearCredential(request);
        controller.validateCredential(request);

        verify(service).savePreference(17L, "tmdb");
        verify(service).saveCredential(17L, "api_key", "test-credential-1234");
        verify(service).clearCredential(17L);
        verify(service).validateCredential(17L);
    }
}
