package com.filmforest.content.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.content.dto.ProfileOverviewView;
import com.filmforest.content.service.ProfileOverviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated profile dashboard endpoint. */
@RestController
@RequestMapping("/api/user/profile")
public class ProfileController {

    private final ProfileOverviewService profileOverviewService;

    public ProfileController(ProfileOverviewService profileOverviewService) {
        this.profileOverviewService = profileOverviewService;
    }

    @GetMapping("/overview")
    public Result<ProfileOverviewView> overview(HttpServletRequest request) {
        Object rawUserId = request.getAttribute("userId");
        if (!(rawUserId instanceof Number number) || number.longValue() <= 0) {
            return Result.fail(401, "未登录，请先登录");
        }
        return Result.ok(profileOverviewService.getOverview(number.longValue()));
    }
}
