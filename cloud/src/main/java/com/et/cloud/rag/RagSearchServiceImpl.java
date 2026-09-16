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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retrieval service with hard permission filtering: the effective space set
 * is computed FIRST (user-visible ∩ requested), then the vector store only
 * ever sees that set. The model is never trusted to withhold results.
 *
 * <p>Ranking pipeline (each stage is optional and degrades independently):
 * <ol>
 *   <li>doc-number exact matches form a PINNED group that always precedes the
 *       rest — that rule layer is this system's real edge over plain vectors
 *       (B-class docRecall@6 = 1.0000 vs 0.333 for vectors alone);</li>
 *   <li>dense channel and BM25 lexical channel each contribute a candidate pool;</li>
 *   <li>reciprocal-rank fusion merges them;</li>
 *   <li>an optional cross-encoder re-ranker orders the pool; failure falls back
 *       to the fused order and never fails the request.</li>
 * </ol>
 *
 * <p>The pool is deliberately deeper than topK: measured on the eval set, even a
 * perfect re-ranker over the dense top-50 caps at recall@6 = 0.8148, so a deep
 * pool is a precondition for any ranking improvement.
 */
@Service
@Slf4j
public class RagSearchServiceImpl implements RagSearchService {

    /** Group size ceiling for the doc-number layer (a doc may legitimately have many chunks). */
    private static final int DOC_NUMBER_GROUP_LIMIT = 200;

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Resource
    private WikiChunkMapper wikiChunkMapper;

    @Resource
    private RagEmbeddingClient ragEmbeddingClient;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private LexicalIndex lexicalIndex;

    @Resource
    private RagRerankClient ragRerankClient;

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

        RagProperties.Retrieval retrieval = ragProperties.getRetrieval();
        int topK = resolveTopK(request, retrieval);
        int poolSize = Math.max(retrieval.getCandidatePoolSize(), topK);
        int fusionSize = Math.max(retrieval.getFusionPoolSize(), topK);

        // 3. doc-number exact-match layer: pinned group, ordered by relevance later.
        //    It must NOT stop the semantic/lexical channels — previously a saturated
        //    exact layer skipped vector search entirely, losing real recall.
        phaseStart = System.currentTimeMillis();
        List<ChunkHit> docNumberGroup = findDocNumberHits(request.getQuery(), effectiveSpaceIds);
        timings.setDocNumberMs(System.currentTimeMillis() - phaseStart);

        // 4. embed the query once: the vector channel needs it, and it also lets the
        //    doc-number group be ordered by relevance when no re-ranker is available
        float[] queryVector = null;
        if (ragEmbeddingClient.isConfigured()) {
            phaseStart = System.currentTimeMillis();
            List<float[]> vectors = ragEmbeddingClient.embed(List.of(request.getQuery().trim()));
            timings.setEmbedMs(System.currentTimeMillis() - phaseStart);
            if (!vectors.isEmpty()) {
                queryVector = vectors.get(0);
            }
        }

        // 5. candidate generation: dense + lexical over the SAME authorized corpus.
        //    A channel that did not run leaves its timing null rather than a
        //    misleading 0 — callers render this as a step timeline.
        List<ChunkHit> vectorHits = Collections.emptyList();
        if (queryVector != null) {
            phaseStart = System.currentTimeMillis();
            vectorHits = vectorStore.search(queryVector, effectiveSpaceIds, poolSize);
            timings.setVectorMs(System.currentTimeMillis() - phaseStart);
        }

        List<ChunkHit> lexicalHits = Collections.emptyList();
        if (retrieval.getLexical().isEnabled()) {
            phaseStart = System.currentTimeMillis();
            lexicalHits = lexicalIndex.search(request.getQuery(), effectiveSpaceIds, poolSize);
            timings.setLexicalMs(System.currentTimeMillis() - phaseStart);
        }

        // 6. fuse the channels (rank-based: cosine and BM25 are not on a common scale)
        phaseStart = System.currentTimeMillis();
        List<ChunkHit> fused = RrfFusion.fuse(List.of(vectorHits, lexicalHits), retrieval.getRrfK(), fusionSize);
        timings.setFusionMs(System.currentTimeMillis() - phaseStart);

        // 7. assemble: pinned doc-number group first, fused results fill the rest
        List<Long> pinnedIds = docNumberGroup.stream().map(ChunkHit::getChunkId).collect(Collectors.toList());
        List<ChunkHit> refined = orderDocNumberGroup(request.getQuery(), docNumberGroup);
        List<ChunkHit> remaining = fused.stream()
                .filter(hit -> !pinnedIds.contains(hit.getChunkId()))
                .collect(Collectors.toList());

        // 8. pinned group keeps its leading slots; the re-ranker only orders what fills the rest.
        //    Re-ranking the pinned group together with the rest would let unrelated chunks
        //    displace it — measured, that costs the doc-number layer ~24pt of recall@6.
        boolean rerankUsable = retrieval.getRerank() != null && retrieval.getRerank().isUsable();
        List<ChunkHit> hits = new ArrayList<>(topK);
        int pinnedSlots = Math.min(refined.size(), topK);
        hits.addAll(refined.subList(0, pinnedSlots));

        int slotsLeft = topK - hits.size();
        if (slotsLeft > 0 && !remaining.isEmpty()) {
            if (rerankUsable) {
                phaseStart = System.currentTimeMillis();
                hits.addAll(rerankOrFallback(request.getQuery(), remaining, slotsLeft));
                // the phase did run even when it failed and fell back — record the cost honestly
                timings.setRerankMs(System.currentTimeMillis() - phaseStart);
            } else {
                hits.addAll(truncate(remaining, slotsLeft));
            }
        }

        result.setHits(hits);
        timings.setTotalMs(System.currentTimeMillis() - totalStart);
        return result;
    }

    /**
     * Re-ranks the candidate pool, or returns it unchanged (already truncated)
     * when re-ranking is unavailable. Any failure degrades to the fused order.
     */
    private List<ChunkHit> rerankOrFallback(String query, List<ChunkHit> candidates, int topK) {
        RagProperties.Rerank config = ragProperties.getRetrieval().getRerank();
        if (config == null || !config.isUsable()) {
            return truncate(candidates, topK);
        }
        int limit = Math.min(candidates.size(), Math.max(1, config.getMaxDocuments()));
        List<ChunkHit> head = candidates.subList(0, limit);
        List<String> documents = new ArrayList<>(limit);
        for (ChunkHit hit : head) {
            documents.add(renderForRerank(hit));
        }
        try {
            List<Integer> order = ragRerankClient.rerank(query, documents, topK);
            if (order.isEmpty()) {
                return truncate(candidates, topK);
            }
            List<ChunkHit> out = new ArrayList<>(topK);
            for (Integer index : order) {
                if (index >= 0 && index < head.size()) {
                    out.add(head.get(index));
                }
                if (out.size() >= topK) {
                    break;
                }
            }
            // fill from the untouched tail if the re-ranker returned fewer than topK
            if (out.size() < topK) {
                for (int i = limit; i < candidates.size() && out.size() < topK; i++) {
                    out.add(candidates.get(i));
                }
            }
            return out;
        } catch (RagRerankUnavailableException e) {
            log.warn("重排失败，回退到融合排序: {}", e.getMessage());
            return truncate(candidates, topK);
        } catch (RuntimeException e) {
            log.warn("重排异常，回退到融合排序: {}", e.toString());
            return truncate(candidates, topK);
        }
    }

    /** Document text handed to the re-ranker: title and heading path carry real topical signal. */
    private static String renderForRerank(ChunkHit hit) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(hit.getDocTitle())) {
            sb.append('《').append(hit.getDocTitle()).append('》');
        }
        if (StrUtil.isNotBlank(hit.getChunkHeading())) {
            sb.append(hit.getChunkHeading());
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(StrUtil.nullToEmpty(hit.getChunkText()));
        return sb.toString();
    }

    /**
     * Orders the pinned doc-number group by relevance. With a re-ranker available
     * the group is ranked by its scores; otherwise the historical chunkIndex order
     * is kept so behaviour does not regress when the channel is off.
     */
    private List<ChunkHit> orderDocNumberGroup(String query, List<ChunkHit> group) {
        RagProperties.Rerank config = ragProperties.getRetrieval().getRerank();
        if (group.size() <= 1 || config == null || !config.isUsable()) {
            return group;
        }
        int limit = Math.min(group.size(), Math.max(1, config.getMaxDocuments()));
        List<ChunkHit> head = group.subList(0, limit);
        List<String> documents = new ArrayList<>(limit);
        for (ChunkHit hit : head) {
            documents.add(renderForRerank(hit));
        }
        try {
            List<Integer> order = ragRerankClient.rerank(query, documents, limit);
            if (order.isEmpty()) {
                return group;
            }
            List<ChunkHit> out = new ArrayList<>(group.size());
            Set<Integer> taken = new LinkedHashSet<>();
            for (Integer index : order) {
                if (index != null && index >= 0 && index < head.size() && taken.add(index)) {
                    out.add(head.get(index));
                }
            }
            // The group must never shrink: a re-ranker that returns fewer indices
            // than documents it was given would otherwise silently drop exact
            // doc-number chunks — the one thing this layer exists to guarantee.
            for (int i = 0; i < head.size(); i++) {
                if (!taken.contains(i)) {
                    out.add(head.get(i));
                }
            }
            for (int i = limit; i < group.size(); i++) {
                out.add(group.get(i));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("文号层内部重排失败，保留原顺序: {}", e.toString());
            return group;
        }
    }

    private static List<ChunkHit> truncate(List<ChunkHit> hits, int topK) {
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    /** Applies the configured default and the hard ceiling to the requested topK. */
    private static int resolveTopK(RagSearchRequest request, RagProperties.Retrieval retrieval) {
        int requested = request.getTopK() != null && request.getTopK() > 0
                ? request.getTopK() : retrieval.getTopK();
        int max = retrieval.getTopKMax() > 0 ? retrieval.getTopKMax() : requested;
        return Math.max(1, Math.min(requested, max));
    }

    /**
     * Extracts a document number from the query (e.g. 渝府办发〔2026〕24号) and returns
     * the ACTIVE chunks of the matching document(s) within the effective scope.
     * Ordered by chunkIndex as a stable default; the caller may re-order by
     * relevance. Returns empty when the query carries no doc number.
     */
    private List<ChunkHit> findDocNumberHits(String query, Set<Long> effectiveSpaceIds) {
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
                .last("LIMIT " + DOC_NUMBER_GROUP_LIMIT);
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
