package com.et.cloud.service;

import com.et.cloud.model.dto.ImportedWikiDocument;
import org.springframework.web.multipart.MultipartFile;

public interface WikiDocumentImportService {

    ImportedWikiDocument parse(MultipartFile multipartFile, String title);
}
