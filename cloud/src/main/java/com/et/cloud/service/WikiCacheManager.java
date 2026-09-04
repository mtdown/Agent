package com.et.cloud.service;

public interface WikiCacheManager {

    void clearSpace(Long spaceId);

    void clearDocument(Long spaceId, Long documentId);
}
