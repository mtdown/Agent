package com.et.cloud.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureReviewRequest implements Serializable {


    private Long id;

    //审核状态
    private Integer reviewStatus;

    //审核信息
    private String reviewMessage;


    private static final long serialVersionUID = 1L;
}