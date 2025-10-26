package com.et.cloud.manager;

import cn.hutool.core.io.FileUtil;
import com.et.cloud.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    // ... 一些操作 COS 的方法

    /**
     * 上传对象
     *
     * @param key  唯一键,文件存在哪
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
//        进行图片压缩
//        第一部是弄出图片规则
        List<PicOperations.Rule> rules = new ArrayList<>();
//mainName可以获取到主要部分，舍弃掉后面的后缀.png
        String webpKey = FileUtil.mainName(key) + ".webp";
//        1. 新建一个“图片处理规则”对象
        PicOperations.Rule compressRule = new PicOperations.Rule();
//        // 2. 设置具体的处理规则：“转成 webp 格式”
        compressRule.setRule("imageMogr2/format/webp");
// 3. 设置处理后文件的存放位置（哪个存储桶）
        compressRule.setBucket(cosClientConfig.getBucket());
        // 4. 设置处理后文件的存放路径和新名字
        compressRule.setFileId(webpKey);
//        5. 把这个“压缩规则”添加到总的“规则列表”中
        rules.add(compressRule);

//        这里开始定义规则2
        PicOperations.Rule thumbnailRule = new PicOperations.Rule();
//        放在哪个包
        thumbnailRule.setBucket(cosClientConfig.getBucket());
        String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
        thumbnailRule.setFileId(thumbnailKey);
//  使用的缩放规则
        thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128));
        rules.add(thumbnailRule);

        picOperations.setRules(rules);
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }


}
