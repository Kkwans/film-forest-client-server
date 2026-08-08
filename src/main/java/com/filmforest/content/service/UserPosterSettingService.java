package com.filmforest.content.service;

import com.filmforest.content.dto.PosterSettingView;
import com.filmforest.content.entity.UserPosterSetting;
import com.filmforest.content.mapper.UserPosterSettingMapper;
import com.filmforest.content.poster.PosterCredentialCipher;
import com.filmforest.content.poster.TmdbCredentialVerifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
public class UserPosterSettingService {

    private static final Set<String> POSTER_SOURCES = Set.of("original", "tmdb");
    private static final Set<String> CREDENTIAL_TYPES = Set.of("api_key", "read_access_token");

    private final UserPosterSettingMapper mapper;
    private final PosterCredentialCipher cipher;
    private final TmdbCredentialVerifier verifier;

    public UserPosterSettingService(UserPosterSettingMapper mapper,
                                    PosterCredentialCipher cipher,
                                    TmdbCredentialVerifier verifier) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.verifier = verifier;
    }

    public PosterSettingView get(long userId) {
        return view(mapper.selectById(userId));
    }

    @Transactional
    public PosterSettingView savePreference(long userId, String posterSource) {
        String normalized = normalize(posterSource, POSTER_SOURCES, "不支持的海报来源");
        UserPosterSetting setting = getOrCreate(userId);
        setting.setPosterSource(normalized);
        persist(setting);
        return view(setting);
    }

    @Transactional
    public PosterSettingView saveCredential(long userId, String credentialType, String credential) {
        String normalizedType = normalize(credentialType, CREDENTIAL_TYPES, "不支持的 TMDB 凭据类型");
        String normalizedCredential = credential == null ? "" : credential.trim();
        if (normalizedCredential.length() < 16 || normalizedCredential.length() > 2048) {
            throw new IllegalArgumentException("TMDB 凭据长度无效");
        }
        PosterCredentialCipher.EncryptedCredential encrypted = cipher.encrypt(userId, normalizedCredential);
        UserPosterSetting setting = getOrCreate(userId);
        setting.setCredentialType(normalizedType);
        setting.setCredentialCiphertext(encrypted.ciphertext());
        setting.setCredentialIv(encrypted.iv());
        setting.setCredentialKeyVersion(encrypted.keyVersion());
        setting.setCredentialHint(mask(normalizedCredential));
        setting.setValidationStatus("unverified");
        setting.setValidationErrorCode(null);
        setting.setValidatedAt(null);
        persist(setting);
        return view(setting);
    }

    @Transactional
    public PosterSettingView clearCredential(long userId) {
        UserPosterSetting setting = mapper.selectById(userId);
        if (setting == null) return defaultView();
        setting.setPosterSource("original");
        setting.setCredentialType(null);
        setting.setCredentialCiphertext(null);
        setting.setCredentialIv(null);
        setting.setCredentialHint(null);
        setting.setValidationStatus("not_configured");
        setting.setValidationErrorCode(null);
        setting.setValidatedAt(null);
        mapper.updateById(setting);
        return view(setting);
    }

    @Transactional
    public PosterSettingView validateCredential(long userId) {
        UserPosterSetting setting = mapper.selectById(userId);
        if (!configured(setting)) return setting == null ? defaultView() : view(setting);
        String credential = cipher.decrypt(userId, setting.getCredentialCiphertext(), setting.getCredentialIv(),
                setting.getCredentialKeyVersion() == null ? 1 : setting.getCredentialKeyVersion());
        TmdbCredentialVerifier.ValidationResult result = verifier.verify(setting.getCredentialType(), credential);
        setting.setValidationStatus(result.status());
        setting.setValidationErrorCode(result.errorCode());
        setting.setValidatedAt(LocalDateTime.now(ZoneOffset.UTC));
        mapper.updateById(setting);
        return view(setting);
    }

    public DecryptedCredential requireCredential(long userId) {
        UserPosterSetting setting = mapper.selectById(userId);
        if (!configured(setting)) throw new IllegalStateException("TMDB 凭据未配置");
        return new DecryptedCredential(setting.getCredentialType(), cipher.decrypt(userId,
                setting.getCredentialCiphertext(), setting.getCredentialIv(),
                setting.getCredentialKeyVersion() == null ? 1 : setting.getCredentialKeyVersion()));
    }

    private UserPosterSetting getOrCreate(long userId) {
        UserPosterSetting existing = mapper.selectById(userId);
        if (existing != null) return existing;
        UserPosterSetting created = new UserPosterSetting();
        created.setUserId(userId);
        created.setPosterSource("original");
        created.setCredentialKeyVersion(1);
        created.setValidationStatus("not_configured");
        return created;
    }

    private void persist(UserPosterSetting setting) {
        if (mapper.selectById(setting.getUserId()) == null) mapper.insert(setting);
        else mapper.updateById(setting);
    }

    private PosterSettingView view(UserPosterSetting setting) {
        if (setting == null) return defaultView();
        boolean configured = configured(setting);
        return new PosterSettingView(
                setting.getPosterSource() == null ? "original" : setting.getPosterSource(),
                configured,
                configured ? setting.getCredentialType() : null,
                configured ? setting.getCredentialHint() : null,
                configured ? setting.getValidationStatus() : "not_configured",
                configured ? setting.getValidationErrorCode() : null,
                configured ? setting.getValidatedAt() : null
        );
    }

    private PosterSettingView defaultView() {
        return new PosterSettingView("original", false, null, null, "not_configured", null, null);
    }

    private boolean configured(UserPosterSetting setting) {
        return setting != null && setting.getCredentialType() != null
                && setting.getCredentialCiphertext() != null && setting.getCredentialIv() != null;
    }

    private String normalize(String value, Set<String> allowed, String message) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String mask(String credential) {
        int visible = Math.min(4, credential.length());
        return "••••" + credential.substring(credential.length() - visible);
    }

    public record DecryptedCredential(String type, String value) {
        @Override
        public String toString() {
            return "DecryptedCredential[type=" + type + ", value=REDACTED]";
        }
    }
}
