package com.filmforest.content.service;

import com.filmforest.content.config.PosterCredentialProperties;
import com.filmforest.content.entity.UserPosterSetting;
import com.filmforest.content.mapper.UserPosterSettingMapper;
import com.filmforest.content.poster.PosterCredentialCipher;
import com.filmforest.content.poster.TmdbCredentialVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPosterSettingServiceTest {

    @Mock private UserPosterSettingMapper mapper;
    @Mock private TmdbCredentialVerifier verifier;

    private UserPosterSettingService service;

    @BeforeEach
    void setUp() {
        String key = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        service = new UserPosterSettingService(mapper,
                new PosterCredentialCipher(new PosterCredentialProperties(key)), verifier);
    }

    @Test
    void saveCredentialScopesCiphertextToAuthenticatedUserAndReturnsOnlyMask() {
        when(mapper.selectById(7L)).thenReturn(null);
        when(mapper.insert(any(UserPosterSetting.class))).thenReturn(1);

        var view = service.saveCredential(7L, "API_KEY", "test-api-key-1234567890");

        ArgumentCaptor<UserPosterSetting> captor = ArgumentCaptor.forClass(UserPosterSetting.class);
        verify(mapper).insert(captor.capture());
        UserPosterSetting stored = captor.getValue();
        assertThat(stored.getUserId()).isEqualTo(7L);
        assertThat(stored.getCredentialCiphertext()).isNotEmpty();
        assertThat(new String(stored.getCredentialCiphertext(), StandardCharsets.UTF_8))
                .doesNotContain("test-api-key");
        assertThat(view.configured()).isTrue();
        assertThat(view.maskedHint()).endsWith("7890");
        assertThat(view.toString()).doesNotContain("test-api-key");
    }

    @Test
    void missingRowReturnsOriginalModeWithoutCredentialFields() {
        when(mapper.selectById(9L)).thenReturn(null);

        var view = service.get(9L);

        assertThat(view.posterSource()).isEqualTo("original");
        assertThat(view.configured()).isFalse();
        assertThat(view.credentialType()).isNull();
        assertThat(view.maskedHint()).isNull();
        assertThat(view.validationStatus()).isEqualTo("not_configured");
    }
}
