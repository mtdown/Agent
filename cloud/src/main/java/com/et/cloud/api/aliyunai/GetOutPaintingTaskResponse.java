package com.et.cloud.api.aliyunai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetOutPaintingTaskResponse {


    private String requestId;


    private Output output;


    @Data
    public static class Output {


        private String taskId;


        private String taskStatus;


        private String submitTime;


        private String scheduledTime;


        private String endTime;


        private String outputImageUrl;


        private String code;


        private String message;


        private TaskMetrics taskMetrics;
    }


    @Data
    public static class TaskMetrics {


        private Integer total;


        private Integer succeeded;


        private Integer failed;
    }
}
