package com.et.cloud.service;

import com.et.cloud.dto.documentWiki.DocumentWikiBatchUrlImportRequest;
import com.et.cloud.model.dto.BatchImportItemResult;
import com.et.cloud.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Batch import of webpages and local documents into a Wiki destination.
 *
 * Every submitted item is isolated: one failing url or file never prevents the remaining items from
 * being imported.
 */
public interface WikiBatchImportService {

    List<BatchImportItemResult> importUrls(DocumentWikiBatchUrlImportRequest request, User loginUser);

    List<BatchImportItemResult> importFiles(Long spaceId, Long folderId, List<MultipartFile> files, User loginUser);
}
