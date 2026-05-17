package com.filmforest.content.dto;

import lombok.Data;

/**
 * 相关推荐 VO
 */
@Data
public class RelatedVO {
    private Long id;
    private String type;       // movie / drama / anime / variety / short_drama
    private String title;
    private String posterUrl;
    private Integer year;
    private Double scoreDouban;
}
