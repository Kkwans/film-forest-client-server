package com.filmforest.content.service;

import com.filmforest.content.dto.ProfileOverviewView;

/** Authenticated profile dashboard aggregation. */
public interface ProfileOverviewService {

    ProfileOverviewView getOverview(Long userId);
}
