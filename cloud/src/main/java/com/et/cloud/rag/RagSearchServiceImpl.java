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
    private RagQueryExpansionClient queryExpansionClient;

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

        // 4. Build query branches. By default this is just the original query;
        //    optional generated branches are lower-weighted retrieval probes, never evidence.
        List<QueryBranch> branches = buildQueryBranches(request.getQuery(), retrieval);

        // 5. embed the query branches once: the vector channel needs them, and the
        //    original vector also lets the doc-number group be ordered by relevance when no re-ranker is available
        List<float[]> queryVectors = Collections.emptyList();
        if (ragEmbeddingClient.isConfigured()) {
            phaseStart = System.currentTimeMillis();
            List<String> branchTexts = branches.stream().map(QueryBranch::getText).collect(Collectors.toList());
            queryVectors = ragEmbeddingClient.embed(branchTexts);
            timings.setEmbedMs(System.currentTimeMillis() - phaseStart);
        }

        // 6. candidate generation: dense + lexical over the SAME authorized corpus.
        //    A channel that did not run leaves its timing null rather than a
        //    misleading 0 — callers render this as a step timeline.
        phaseStart = System.currentTimeMillis();
        List<List<ChunkHit>> vectorBranchHits = new ArrayList<>();
        if (!queryVectors.isEmpty()) {
            for (float[] queryVector : queryVectors) {
                vectorBranchHits.add(vectorStore.search(queryVector, effectiveSpaceIds, poolSize));
            }
            timings.setVectorMs(System.currentTimeMillis() - phaseStart);
        }

        phaseStart = System.currentTimeMillis();
        List<List<ChunkHit>> lexicalBranchHits = new ArrayList<>();
        if (retrieval.getLexical().isEnabled()) {
            for (QueryBranch branch : branches) {
                lexicalBranchHits.add(lexicalIndex.search(branch.getText(), effectiveSpaceIds, poolSize));
            }
            timings.setLexicalMs(System.currentTimeMillis() - phaseStart);
        }

        // 7. fuse the channels (rank-based: cosine and BM25 are not on a common scale),
        //    then fuse branches with fixed weights: original 1.0, rewrite 0.7, hypothetical answer 0.6.
        phaseStart = System.currentTimeMillis();
        List<ChunkHit> fused = fuseBranches(branches, vectorBranchHits, lexicalBranchHits, retrieval, fusionSize);
        timings.setFusionMs(System.currentTimeMillis() - phaseStart);

        // 8. assemble: pinned doc-number group first, fused results fill the rest
        List<Long> pinnedIds = docNumberGroup.stream().map(ChunkHit::getChunkId).collect(Collectors.toList());
        List<ChunkHit> refined = orderDocNumberGroup(request.getQuery(), docNumberGroup);
        List<ChunkHit> remaining = fused.stream()
                .filter(hit -> !pinnedIds.contains(hit.getChunkId()))
                .collect(Collectors.toList());
        remaining = assembleEvidence(remaining, retrieval.getEvidenceAssembly(), fusionSize);

        // 9. pinned group keeps its leading slots; the re-ranker only orders what fills the rest.
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

    private List<QueryBranch> buildQueryBranches(String originalQuery, RagProperties.Retrieval retrieval) {
        String original = originalQuery.trim();
        List<QueryBranch> branches = new ArrayList<>();
        RagProperties.MultiQuery config = retrieval.getMultiQuery();
        branches.add(new QueryBranch(original, Math.max(0.0d, config.getOriginalWeight())));
        if (config == null || !config.isEnabled()) {
            return branches;
        }
        try {
            RagQueryExpansion expansion = queryExpansionClient.expand(original);
            if (expansion != null && StrUtil.isNotBlank(expansion.getRewrittenQuestion())) {
                branches.add(new QueryBranch(expansion.getRewrittenQuestion().trim(),
                        Math.max(0.0d, config.getRewrittenQuestionWeight())));
            }
            if (expansion != null && StrUtil.isNotBlank(expansion.getHypotheticalAnswer())) {
                branches.add(new QueryBranch(expansion.getHypotheticalAnswer().trim(),
                        Math.max(0.0d, config.getHypotheticalAnswerWeight())));
            }
            // 多查询 A/B 跑批必须能自证分支真的生成了：expansion 静默返回空会让链路
            // 退回单查询，而指标照样出数 —— 不记录分支数就无法区分"没效果"和"没生效"。
            if (branches.size() == 1) {
                log.warn("多查询已启用但未生成任何分支（expansion 为空），本次按原问题检索");
            } else {
                log.info("RAG multi-query branches={} (rewrite={}, hypotheticalAnswer={})",
                        branches.size(),
                        expansion != null && StrUtil.isNotBlank(expansion.getRewrittenQuestion()),
                        expansion != null && StrUtil.isNotBlank(expansion.getHypotheticalAnswer()));
            }
        } catch (RuntimeException e) {
            log.warn("多查询生成失败，回退到原问题检索: {}", e.toString());
        }
        return branches;
    }

    private List<ChunkHit> fuseBranches(List<QueryBranch> branches,
                                        List<List<ChunkHit>> vectorBranchHits,
                                        List<List<ChunkHit>> lexicalBranchHits,
                                        RagProperties.Retrieval retrieval,
                                        int fusionSize) {
        if (branches.size() == 1) {
            List<ChunkHit> vectorHits = vectorBranchHits.isEmpty() ? Collections.emptyList() : vectorBranchHits.get(0);
            List<ChunkHit> lexicalHits = lexicalBranchHits.isEmpty() ? Collections.emptyList() : lexicalBranchHits.get(0);
            return RrfFusion.fuse(List.of(vectorHits, lexicalHits), retrieval.getRrfK(), fusionSize);
        }
        java.util.LinkedHashMap<Long, ChunkHit> byId = new java.util.LinkedHashMap<>();
        java.util.Map<Long, Double> scores = new java.util.HashMap<>();
        for (int i = 0; i < branches.size(); i++) {
            List<ChunkHit> vectorHits = i < vectorBranchHits.size() ? vectorBranchHits.get(i) : Collections.emptyList();
            List<ChunkHit> lexicalHits = i < lexicalBranchHits.size() ? lexicalBranchHits.get(i) : Collections.emptyList();
            List<ChunkHit> branchFused = RrfFusion.fuse(List.of(vectorHits, lexicalHits), retrieval.getRrfK(), fusionSize);
            double weight = branches.get(i).getWeight();
            for (int rank = 0; rank < branchFused.size(); rank++) {
                ChunkHit hit = branchFused.get(rank);
                byId.putIfAbsent(hit.getChunkId(), hit);
                scores.merge(hit.getChunkId(), weight / (retrieval.getRrfK() + rank + 1.0d), Double::sum);
            }
        }
        List<ChunkHit> out = new ArrayList<>(byId.values());
        out.sort((a, b) -> Double.compare(scores.getOrDefault(b.getChunkId(), 0.0d),
                scores.getOrDefault(a.getChunkId(), 0.0d)));
        for (ChunkHit hit : out) {
            hit.setScore(scores.getOrDefault(hit.getChunkId(), hit.getScore()));
        }
        return truncate(out, fusionSize);
    }

    private List<ChunkHit> assembleEvidence(List<ChunkHit> hits, RagProperties.EvidenceAssembly config, int limit) {
        if (config == null || !config.isEnabled() || hits.size() <= 1) {
            return hits;
        }
        List<ChunkHit> merged = mergeSameDocumentAdjacent(hits, config);
        assignCrossDocumentGroups(merged);
        List<ChunkHit> out = truncate(merged, limit);
        // chunk 整理同样会"静默无效"：开关打开但一对可合并的相邻 chunk 都没有时，
        // 输出与输入逐条相同，指标照出数。不记录就无法区分"没效果"和"没生效"——
        // 多查询分支已经踩过这个坑，这里同样必须能自证。
        int mergedBlocks = 0;
        int maxBlockUnits = 0;
        int coveredUnits = 0;
        Set<String> groups = new LinkedHashSet<>();
        for (ChunkHit hit : out) {
            int units = hit.getOriginalChunkIds().isEmpty() ? 1 : hit.getOriginalChunkIds().size();
            coveredUnits += units;
            if (units > 1) {
                mergedBlocks++;
            }
            maxBlockUnits = Math.max(maxBlockUnits, units);
            if (hit.getEvidenceGroupId() != null) {
                groups.add(hit.getEvidenceGroupId());
            }
        }
        log.info("RAG evidence-assembly in={} out={} mergedBlocks={} maxBlockUnits={} coveredUnits={} groups={}",
                hits.size(), out.size(), mergedBlocks, maxBlockUnits, coveredUnits, groups.size());
        return out;
    }

    private List<ChunkHit> mergeSameDocumentAdjacent(List<ChunkHit> hits, RagProperties.EvidenceAssembly config) {
        List<ChunkHit> out = new ArrayList<>();
        Set<Long> used = new LinkedHashSet<>();
        for (ChunkHit seed : hits) {
            if (seed.getChunkId() == null || used.contains(seed.getChunkId())) {
                continue;
            }
            List<ChunkHit> block = new ArrayList<>();
            block.add(seed);
            used.add(seed.getChunkId());
            for (ChunkHit candidate : hits) {
                if (candidate.getChunkId() == null || used.contains(candidate.getChunkId())) {
                    continue;
                }
                if (!canMerge(block, candidate, config)) {
                    continue;
                }
                block.add(candidate);
                used.add(candidate.getChunkId());
                block.sort(java.util.Comparator.comparing(ChunkHit::getChunkIndex,
                        java.util.Comparator.nullsLast(Integer::compareTo)));
                if (block.size() >= config.getMaxChunksPerBlock()) {
                    break;
                }
            }
            out.add(block.size() == 1 ? seed : mergeBlock(block));
        }
        return out;
    }

    private boolean canMerge(List<ChunkHit> block, ChunkHit candidate, RagProperties.EvidenceAssembly config) {
        ChunkHit first = block.get(0);
        if (first.getDocId() == null || !first.getDocId().equals(candidate.getDocId())) {
            return false;
        }
        if (!compatibleHeading(first.getChunkHeading(), candidate.getChunkHeading())) {
            return false;
        }
        List<ChunkHit> trial = new ArrayList<>(block);
        trial.add(candidate);
        if (trial.size() > config.getMaxChunksPerBlock()) {
            return false;
        }
        trial.sort(java.util.Comparator.comparing(ChunkHit::getChunkIndex,
                java.util.Comparator.nullsLast(Integer::compareTo)));
        for (int i = 1; i < trial.size(); i++) {
            Integer prev = trial.get(i - 1).getChunkIndex();
            Integer curr = trial.get(i).getChunkIndex();
            if (prev == null || curr == null || Math.abs(curr - prev) > config.getMaxChunkIndexGap()) {
                return false;
            }
        }
        int chars = 0;
        for (ChunkHit hit : trial) {
            chars += StrUtil.nullToEmpty(hit.getChunkText()).length();
        }
        return chars <= config.getMaxMergedChars();
    }

    private static boolean compatibleHeading(String a, String b) {
        String left = StrUtil.nullToEmpty(a).trim();
        String right = StrUtil.nullToEmpty(b).trim();
        if (left.equals(right)) {
            return true;
        }
        return !parentHeading(left).isEmpty() && parentHeading(left).equals(parentHeading(right));
    }

    private static String parentHeading(String heading) {
        int slash = heading.lastIndexOf('/');
        if (slash < 0) {
            slash = heading.lastIndexOf('>');
        }
        return slash <= 0 ? "" : heading.substring(0, slash).trim();
    }

    private static ChunkHit mergeBlock(List<ChunkHit> block) {
        block.sort(java.util.Comparator.comparing(ChunkHit::getChunkIndex,
                java.util.Comparator.nullsLast(Integer::compareTo)));
        ChunkHit first = block.get(0);
        ChunkHit merged = new ChunkHit();
        merged.setChunkId(first.getChunkId());
        merged.setDocId(first.getDocId());
        merged.setSpaceId(first.getSpaceId());
        merged.setChunkIndex(first.getChunkIndex());
        merged.setChunkHeading(first.getChunkHeading());
        merged.setDocTitle(first.getDocTitle());
        merged.setDocNumber(first.getDocNumber());
        StringBuilder text = new StringBuilder();
        double maxScore = Double.NEGATIVE_INFINITY;
        List<Long> originalIds = new ArrayList<>();
        List<Integer> originalIndexes = new ArrayList<>();
        List<Double> originalScores = new ArrayList<>();
        for (ChunkHit hit : block) {
            if (text.length() > 0) {
                text.append("\n");
            }
            text.append(StrUtil.nullToEmpty(hit.getChunkText()));
            maxScore = Math.max(maxScore, hit.getScore());
            originalIds.addAll(hit.getOriginalChunkIds());
            originalIndexes.addAll(hit.getOriginalChunkIndexes());
            originalScores.addAll(hit.getOriginalChunkScores());
        }
        merged.setChunkText(text.toString());
        merged.setScore(maxScore == Double.NEGATIVE_INFINITY ? first.getScore() : maxScore);
        merged.setOriginalChunkIds(originalIds);
        merged.setOriginalChunkIndexes(originalIndexes);
        merged.setOriginalChunkScores(originalScores);
        return merged;
    }

    private static void assignCrossDocumentGroups(List<ChunkHit> hits) {
        int group = 1;
        Set<Long> seenDocs = new LinkedHashSet<>();
        for (ChunkHit hit : hits) {
            if (hit.getDocId() != null && seenDocs.add(hit.getDocId()) && seenDocs.size() > 1) {
                String id = "evidence-group-" + group;
                for (ChunkHit member : hits) {
                    if (member.getEvidenceGroupId() == null) {
                        member.setEvidenceGroupId(id);
                    }
                }
                return;
            }
        }
    }

    private static final class QueryBranch {
        private final String text;
        private final double weight;

        private QueryBranch(String text, double weight) {
            this.text = text;
            this.weight = weight;
        }

        private String getText() {
            return text;
        }

        private double getWeight() {
            return weight;
        }
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
