package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.PosterPreferenceRequest;
import com.filmforest.content.dto.PosterSettingView;
import com.filmforest.content.dto.TmdbCredentialRequest;
import com.filmforest.content.service.UserPosterSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/poster/settings")
public class PosterSettingController {

    private final UserPosterSettingService service;

    public PosterSettingController(UserPosterSettingService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PosterSettingView> get(HttpServletRequest request) {
        return Result.ok(service.get(userId(request)));
    }

    @PutMapping("/preference")
    public Result<PosterSettingView> savePreference(@Valid @RequestBody PosterPreferenceRequest body,
                                                    HttpServletRequest request) {
        return Result.ok(service.savePreference(userId(request), body.posterSource()));
    }

    @PutMapping("/credential")
    public Result<PosterSettingView> saveCredential(@Valid @RequestBody TmdbCredentialRequest body,
                                                    HttpServletRequest request) {
        return Result.ok(service.saveCredential(userId(request), body.credentialType(), body.credential()));
    }

    @DeleteMapping("/credential")
    public Result<PosterSettingView> clearCredential(HttpServletRequest request) {
        return Result.ok(service.clearCredential(userId(request)));
    }

    @PostMapping("/credential/validate")
    public Result<PosterSettingView> validateCredential(HttpServletRequest request) {
        return Result.ok(service.validateCredential(userId(request)));
    }

    private long userId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalStateException("认证用户缺失");
        return userId;
    }
}
