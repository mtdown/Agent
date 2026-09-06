package com.et.cloud.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.et.cloud.config.CosClientConfig;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.manager.CosManager;
import com.et.cloud.mapper.WikiAttachmentMapper;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiAttachment;
import com.et.cloud.model.entity.WikiSpace;
import com.et.cloud.service.DocumentWikiService;
import com.et.cloud.service.WikiAttachmentService;
import com.et.cloud.service.WikiSpaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class WikiAttachmentServiceImpl extends ServiceImpl<WikiAttachmentMapper, WikiAttachment>
        implements WikiAttachmentService {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;

    private static final List<String> ALLOW_IMAGE_SUFFIX_LIST = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private DocumentWikiService documentWikiService;

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    @Override
    public void validImage(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        long fileSize = multipartFile.getSize();
        ThrowUtils.throwIf(fileSize > MAX_IMAGE_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过 5M");
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(StrUtil.isBlank(fileSuffix) || !ALLOW_IMAGE_SUFFIX_LIST.contains(fileSuffix.toLowerCase()),
                ErrorCode.PARAMS_ERROR, "文件类型错误");
        ThrowUtils.throwIf(!matchesImageHeader(multipartFile, fileSuffix.toLowerCase()),
                ErrorCode.PARAMS_ERROR, "文件内容不是有效图片");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadImage(MultipartFile multipartFile, Long wikiSpaceId, Long documentId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        validImage(multipartFile);
        WikiSpace wikiSpace = wikiSpaceService.requireEditableSpace(wikiSpaceId, loginUser);
        validateDocumentInSpace(documentId, wikiSpace.getId());
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename()).toLowerCase();
        String uuid = RandomUtil.randomString(16);
        String uploadPath = String.format("wiki/%s/%s.%s", wikiSpace.getId(), uuid, fileSuffix);
        File tempFile = null;
        try {
            tempFile = File.createTempFile(uuid, null);
            multipartFile.transferTo(tempFile);
            cosManager.putObject(uploadPath, tempFile);
            String url = cosClientConfig.getHost() + "/" + uploadPath;
            WikiAttachment wikiAttachment = new WikiAttachment();
            wikiAttachment.setWikiSpaceId(wikiSpace.getId());
            wikiAttachment.setDocumentId(documentId);
            wikiAttachment.setFileName(multipartFile.getOriginalFilename());
            wikiAttachment.setUrl(url);
            wikiAttachment.setFileSize(multipartFile.getSize());
            wikiAttachment.setMimeType(StrUtil.blankToDefault(multipartFile.getContentType(), "image/" + fileSuffix));
            wikiAttachment.setFileHash(md5Hex(tempFile));
            wikiAttachment.setUserId(loginUser.getId());
            boolean result = this.save(wikiAttachment);
            if (!result) {
                deleteUploadedObject(uploadPath);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "附件记录保存失败");
            }
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Wiki image upload failed, wikiSpaceId = {}", wikiSpaceId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private void validateDocumentInSpace(Long documentId, Long wikiSpaceId) {
        if (documentId == null) {
            return;
        }
        DocumentWiki documentWiki = documentWikiService.getById(documentId);
        ThrowUtils.throwIf(documentWiki == null, ErrorCode.PARAMS_ERROR, "文档不存在");
        ThrowUtils.throwIf(!Objects.equals(documentWiki.getSpaceId(), wikiSpaceId),
                ErrorCode.NO_AUTH_ERROR, "不能关联其他空间的文档");
    }

    private boolean matchesImageHeader(MultipartFile multipartFile, String suffix) {
        byte[] header = new byte[12];
        int read;
        try (InputStream inputStream = multipartFile.getInputStream()) {
            read = inputStream.read(header);
        } catch (IOException e) {
            return false;
        }
        if (read < 4) {
            return false;
        }
        switch (suffix) {
            case "jpg":
            case "jpeg":
                return (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png":
                return read >= 8
                        && (header[0] & 0xFF) == 0x89
                        && header[1] == 'P'
                        && header[2] == 'N'
                        && header[3] == 'G'
                        && header[4] == 13
                        && header[5] == 10
                        && header[6] == 26
                        && header[7] == 10;
            case "gif":
                return read >= 6
                        && header[0] == 'G'
                        && header[1] == 'I'
                        && header[2] == 'F'
                        && header[3] == '8'
                        && (header[4] == '7' || header[4] == '9')
                        && header[5] == 'a';
            case "webp":
                return read >= 12
                        && header[0] == 'R'
                        && header[1] == 'I'
                        && header[2] == 'F'
                        && header[3] == 'F'
                        && header[8] == 'W'
                        && header[9] == 'E'
                        && header[10] == 'B'
                        && header[11] == 'P';
            default:
                return false;
        }
    }

    private void deleteUploadedObject(String uploadPath) {
        try {
            cosManager.deleteObject(uploadPath);
        } catch (Exception e) {
            log.error("Wiki image compensation delete failed, uploadPath = {}", uploadPath, e);
        }
    }

    /**
     * Hash our own temp file: multipartFile.getBytes() is unusable here because
     * transferTo() already consumed Tomcat's underlying upload temp file.
     */
    private String md5Hex(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return DigestUtils.md5DigestAsHex(in);
        }
    }

    private void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }
}
