package com.filmforest.content.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Profile dashboard item projection, with list context for custom previews. */
@Data
public class ProfileOverviewItemView {

    private Long id;
    private Long listId;
    private String listName;
    private String listType;
    private Long movieId;
    private String contentType;
    private OffsetDateTime addedAt;
    private OffsetDateTime watchedAt;
    private String title;
    private String cover;
    private Integer year;
    private BigDecimal rating;
    private BigDecimal userRating;
    private String note;
    private String region;
    private String genre;
    private Integer duration;
    private Integer totalEpisode;
}
