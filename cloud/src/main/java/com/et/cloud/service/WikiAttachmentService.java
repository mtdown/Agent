package com.et.cloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiAttachment;
import org.springframework.web.multipart.MultipartFile;

/**
 * Wiki attachment service. Permission is based on wiki space visibility,
 * never on the picture module's space system.
 */
public interface WikiAttachmentService extends IService<WikiAttachment> {

    /**
     * Validate an image attachment: not empty, size <= 5MB, suffix whitelist.
     */
    void validImage(MultipartFile multipartFile);

    /**
     * Upload an image into a visible wiki space and persist the attachment record.
     *
     * @param multipartFile image file
     * @param wikiSpaceId   target wiki space, must be visible to loginUser
     * @param documentId    optional linked document
     * @param loginUser     current logged-in user
     * @return accessible file url
     */
    String uploadImage(MultipartFile multipartFile, Long wikiSpaceId, Long documentId, User loginUser);
}
