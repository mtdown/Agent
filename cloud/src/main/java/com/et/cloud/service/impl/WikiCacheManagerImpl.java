package com.et.cloud.service.impl;

import com.et.cloud.service.WikiCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Set;

@Service
public class WikiCacheManagerImpl implements WikiCacheManager {

    private static final String DETAIL_CACHE_KEY_PREFIX = "agentWiki:documentWiki:detail:";

    private static final String LIST_CACHE_KEY_PREFIX = "agentWiki:documentWiki:list:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void clearSpace(Long spaceId) {
        if (spaceId == null) {
            return;
        }
        Set<String> keys = stringRedisTemplate.keys(LIST_CACHE_KEY_PREFIX + spaceId + ":*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        clearAllListCache();
    }

    private void clearAllListCache() {
        Set<String> keys = stringRedisTemplate.keys(LIST_CACHE_KEY_PREFIX + "all:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Override
    public void clearDocument(Long spaceId, Long documentId) {
        if (spaceId != null && documentId != null) {
            stringRedisTemplate.delete(DETAIL_CACHE_KEY_PREFIX + spaceId + ":" + documentId);
        }
        clearSpace(spaceId);
    }
}
