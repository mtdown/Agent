package com.et.cloud.rag;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.exception.ThrowUtils;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.User;
import com.et.cloud.model.entity.WikiChunk;
import com.et.cloud.service.WikiSpaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retrieval service with hard permission filtering: the effective space set
 * is computed FIRST (user-visible ∩ requested), then the vector store only
 * ever sees that set. The model is never trusted to withhold results.
 */
@Service
@Slf4j
public class RagSearchServiceImpl implements RagSearchService {

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private WikiChunkMapper wikiChunkMapper;

    @Resource
    private RagEmbeddingClient ragEmbeddingClient;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private RagProperties ragProperties;

    @Override
    public RagSearchResult search(User loginUser, RagSearchRequest request) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getQuery()),
                ErrorCode.PARAMS_ERROR, "查询内容不能为空");

        long totalStart = System.currentTimeMillis();
        SearchTimings timings = new SearchTimings();
        RagSearchResult result = new RagSearchResult();
        result.setTimings(timings);

        // 1. permission filter FIRST: effective = visible ∩ requested
        long phaseStart = System.currentTimeMillis();
        List<Long> visibleSpaceIds = wikiSpaceService.listVisibleSpaceIds(loginUser);
        Set<Long> effectiveSpaceIds = new LinkedHashSet<>();
        if (CollUtil.isEmpty(request.getSpaceIds())) {
            effectiveSpaceIds.addAll(visibleSpaceIds);
        } else {
            Set<Long> visibleSet = new LinkedHashSet<>(visibleSpaceIds);
            for (Long spaceId : request.getSpaceIds()) {
                if (spaceId != null && visibleSet.contains(spaceId)) {
                    effectiveSpaceIds.add(spaceId);
                }
            }
        }
        timings.setPermissionMs(System.currentTimeMillis() - phaseStart);

        result.setEffectiveSpaceIds(new LinkedHashSet<>(effectiveSpaceIds));
        if (effectiveSpaceIds.isEmpty()) {
            // requested scope contains no visible space: nothing is searchable
            timings.setTotalMs(System.currentTimeMillis() - totalStart);
            return result;
        }

        // 2. authorized document count within the effective scope
        phaseStart = System.currentTimeMillis();
        result.setAuthorizedDocCount(countActiveDocs(effectiveSpaceIds));
        timings.setDocCountMs(System.currentTimeMillis() - phaseStart);

        int topK = request.getTopK() != null && request.getTopK() > 0
                ? request.getTopK() : ragProperties.getRetrieval().getTopK();

        // 3. doc-number exact-match layer: 纯向量对文号精确查询不敏感（实测〔2026〕24号排在 14/6/34 号之后），
        //    查询里出现文号时先按 docNumber 精确命中，向量检索只补剩余名额 —— 轻量混合检索，无需 BM25
        phaseStart = System.currentTimeMillis();
        List<ChunkHit> exactHits = findDocNumberHits(request.getQuery(), effectiveSpaceIds, topK);
        timings.setDocNumberMs(System.currentTimeMillis() - phaseStart);

        // 4. embed the query and search only within authorized spaces
        List<ChunkHit> hits = new ArrayList<>();
        Set<Long> exactChunkIds = new java.util.HashSet<>();
        for (ChunkHit hit : exactHits) {
            exactChunkIds.add(hit.getChunkId());
            if (hits.size() < topK) {
                hits.add(hit);
            }
        }
        if (hits.size() < topK) {
            phaseStart = System.currentTimeMillis();
            List<float[]> vectors = ragEmbeddingClient.embed(List.of(request.getQuery().trim()));
            timings.setEmbedMs(System.currentTimeMillis() - phaseStart);
            float[] queryVector = vectors.get(0);
            phaseStart = System.currentTimeMillis();
            List<ChunkHit> vectorHits = vectorStore.search(queryVector, effectiveSpaceIds, topK);
            timings.setVectorMs(System.currentTimeMillis() - phaseStart);
            for (ChunkHit hit : vectorHits) {
                if (hits.size() >= topK) {
                    break;
                }
                if (!exactChunkIds.contains(hit.getChunkId())) {
                    hits.add(hit);
                }
            }
        }
        result.setHits(hits);
        timings.setTotalMs(System.currentTimeMillis() - totalStart);
        return result;
    }

    /**
     * Extracts a document number from the query (e.g. 渝府办发〔2026〕24号) and returns
     * the ACTIVE chunks of the matching document(s) within the effective scope, ordered
     * by chunkIndex. Returns empty when the query carries no doc number.
     */
    private List<ChunkHit> findDocNumberHits(String query, Set<Long> effectiveSpaceIds, int topK) {
        java.util.regex.Matcher matcher = MarkdownChunker.DOC_NUMBER_PATTERN.matcher(query);
        if (!matcher.find()) {
            return new ArrayList<>();
        }
        String docNumber = matcher.group();
        QueryWrapper<WikiChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("docNumber", docNumber)
                .in("spaceId", effectiveSpaceIds)
                .eq("status", WikiChunk.STATUS_ACTIVE)
                .eq("isDelete", 0)
                .orderByAsc("chunkIndex")
                .last("LIMIT " + Math.max(1, topK));
        List<WikiChunk> chunks = wikiChunkMapper.selectList(wrapper);
        List<ChunkHit> hits = new ArrayList<>();
        if (chunks == null) {
            return hits;
        }
        for (WikiChunk chunk : chunks) {
            // score = 1.0 代表文号精确命中（高于一切余弦相似度），前端可据此显示"精确匹配"
            hits.add(new ChunkHit(chunk.getId(), chunk.getDocId(), chunk.getSpaceId(), chunk.getChunkIndex(),
                    chunk.getChunkHeading(), chunk.getChunkText(), chunk.getDocTitle(), chunk.getDocNumber(), 1.0d));
        }
        log.info("RAG doc-number exact match: query contains [{}], {} chunks hit", docNumber, hits.size());
        return hits;
    }

    private long countActiveDocs(Set<Long> spaceIds) {
        if (spaceIds.isEmpty()) {
            return 0;
        }
        QueryWrapper<WikiChunk> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT docId").in("spaceId", spaceIds)
                .eq("status", WikiChunk.STATUS_ACTIVE).eq("isDelete", 0);
        List<Object> docIds = wikiChunkMapper.selectObjs(wrapper);
        return docIds == null ? 0 : docIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toList()).size();
    }
}
