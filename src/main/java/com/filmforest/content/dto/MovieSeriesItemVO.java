package com.filmforest.content.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 详情页系列下拉列表中的最小影片信息。 */
@Data
@AllArgsConstructor
public class MovieSeriesItemVO {
    private Long id;
    private String title;
    private Integer year;
    private Integer seriesOrder;
}
