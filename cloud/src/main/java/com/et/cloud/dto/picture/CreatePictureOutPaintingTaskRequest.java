package com.et.cloud.dto.picture;

import com.et.cloud.api.aliyunai.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {
//    主要是需要图片ID，这样就能定位到图片，回头存的时候调用上传的接口呗
    private Long pictureId;

    private CreateOutPaintingTaskRequest.Parameters parameters;

    private static final long serialVersionUID = 1L;
}
