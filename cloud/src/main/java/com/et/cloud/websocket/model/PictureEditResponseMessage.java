package com.et.cloud.websocket.model;

import com.et.cloud.model.vis.UserVis;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditResponseMessage {


    private String type;


    private String message;


    private String editAction;


    private UserVis user;
}
