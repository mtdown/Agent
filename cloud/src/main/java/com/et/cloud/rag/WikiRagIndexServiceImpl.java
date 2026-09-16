package com.et.cloud.rag;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.mapper.DocumentWikiMapper;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.DocumentWiki;
import com.et.cloud.model.entity.WikiChunk;
import com.et.cloud.service.WikiChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Chunk indexing pipeline. Everything here runs off the request path; failures
 * are logged and swallowed so document operations stay unaffected.
 */
@Service
@Slf4j
public class WikiRagIndexServiceImpl implements WikiRagIndexService {

    private static final String CONTENT_FORMAT_MARKDOWN = "markdown";

    private static final int TEXT_PREVIEW_LENGTH = 160;

    @Resource
    private DocumentWikiMapper documentWikiMapper;

    @Resource
    private WikiChunkMapper wikiChunkMapper;

    @Resource
    private WikiChunkService wikiChunkService;

    @Resource
    private RagEmbeddingClient ragEmbeddingClient;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private VectorStore vectorStore;

    @Override
    public void indexDocument(Long docId) {
        try {
            doIndexDocument(docId);
        } catch (RagEmbeddingUnavailableException e) {
            log.warn("doc {} 索引降级（embedding 不可用，待回填兜底）: {}", docId, e.getMessage());
        } catch (Exception e) {
            log.error("doc {} 索引失败（不影响文档保存，回填/对账兜底）", docId, e);
        }
    }

    private void doIndexDocument(Long docId) {
        DocumentWiki doc = documentWikiMapper.selectById(docId);
        if (doc == null || (doc.getIsDelete() != null && doc.getIsDelete() == 1)) {
            // doc vanished between commit and indexing (e.g. quick delete) — nothing to do
            wikiChunkMapper.invalidateByDocId(docId);
            return;
        }
        Long spaceId = doc.getSpaceId();
        // non-markdown documents are out of RAG scope this phase
        wikiChunkMapper.invalidateByDocId(docId);
        if (!CONTENT_FORMAT_MARKDOWN.equals(doc.getContentFormat()) || StrUtil.isBlank(doc.getContent())) {
            vectorStore.onChunksChanged(spaceId);
            return;
        }
        int version = doc.getContentVersion() == null ? 1 : doc.getContentVersion();
        ChunkerProfile profile = ChunkerProfile.resolve(doc.getContent(), metadataLanguage(doc.getMetadataJson()));
        List<MarkdownChunk> slices = MarkdownChunker.chunk(doc.getContent(), profile);
        if (slices.isEmpty()) {
            vectorStore.onChunksChanged(spaceId);
            return;
        }
        String docNumber = MarkdownChunker.extractDocNumber(doc.getContent());
        String title = StrUtil.blankToDefault(doc.getTitle(), "");
        List<WikiChunk> rows = new ArrayList<>(slices.size());
        int batchSize = Math.max(1, ragProperties.getIndex().getBatchEmbedSize());
        for (int start = 0; start < slices.size(); start += batchSize) {
            List<MarkdownChunk> batch = slices.subList(start, Math.min(start + batchSize, slices.size()));
            List<String> texts = new ArrayList<>(batch.size());
            for (MarkdownChunk slice : batch) {
                texts.add(slice.getText());
            }
            List<float[]> vectors = ragEmbeddingClient.embed(texts);
            for (int i = 0; i < batch.size(); i++) {
                MarkdownChunk slice = batch.get(i);
                WikiChunk row = new WikiChunk();
                row.setDocId(docId);
                row.setSpaceId(spaceId);
                row.setContentVersion(version);
                row.setChunkIndex(slice.getIndex());
                row.setChunkHeading(StrUtil.maxLength(slice.getHeadingPath(), 512));
                row.setChunkText(slice.getText());
                row.setDocTitle(StrUtil.maxLength(title, 256));
                row.setDocNumber(docNumber);
                row.setEmbedding(VectorCodec.encode(vectors.get(i)));
                row.setStatus(WikiChunk.STATUS_ACTIVE);
                row.setCreateTime(new Date());
                rows.add(row);
            }
        }
        wikiChunkService.saveBatch(rows);
        vectorStore.onChunksChanged(spaceId);
        log.info("doc {} indexed: {} chunks, version {}, profile {}", docId, rows.size(), version, profile.getName());
    }

    /**
     * Language recorded on the document when it was imported, or null when it carries none.
     *
     * <p>Preferring the stored value keeps a rebuild in step with the chunks produced right after the
     * import: re-detecting the language here could select the other profile, renumber every chunk and
     * invalidate evaluation anchors that are already bound to chunk indexes.
     */
    private static String metadataLanguage(String metadataJson) {
        if (StrUtil.isBlank(metadataJson)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(metadataJson).getStr("language");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void invalidateDocument(Long docId) {
        try {
            DocumentWiki doc = documentWikiMapper.selectById(docId);
            int updated = wikiChunkMapper.invalidateByDocId(docId);
            if (doc != null && updated > 0) {
                vectorStore.onChunksChanged(doc.getSpaceId());
            }
            log.info("doc {} chunks invalidated: {}", docId, updated);
        } catch (Exception e) {
            log.error("doc {} 索引失效失败（对账兜底）", docId, e);
        }
    }

    @Override
    public void reactivateDocument(Long docId, Integer contentVersion) {
        try {
            DocumentWiki doc = documentWikiMapper.selectById(docId);
            if (doc == null || (doc.getIsDelete() != null && doc.getIsDelete() == 1)) {
                return;
            }
            int version = contentVersion == null
                    ? (doc.getContentVersion() == null ? 1 : doc.getContentVersion())
                    : contentVersion;
            int reactivated = wikiChunkMapper.reactivateByDocIdAndVersion(docId, version);
            if (reactivated > 0) {
                vectorStore.onChunksChanged(doc.getSpaceId());
                log.info("doc {} chunks reactivated: {}", docId, reactivated);
            } else {
                // version drifted while in the recycle bin — rebuild
                indexDocument(docId);
            }
        } catch (Exception e) {
            log.error("doc {} 索引恢复失败（对账兜底）", docId, e);
        }
    }

    @Override
    public void deleteDocumentChunks(Long docId, Long spaceId) {
        try {
            wikiChunkMapper.physicallyDeleteByDocId(docId);
            if (spaceId != null) {
                vectorStore.onChunksChanged(spaceId);
            }
            log.info("doc {} chunks physically deleted", docId);
        } catch (Exception e) {
            log.error("doc {} chunk 物理删除失败（对账兜底）", docId, e);
        }
    }

    @Override
    public void moveDocumentChunks(Long docId, Long fromSpaceId, Long toSpaceId) {
        try {
            wikiChunkMapper.moveByDocId(docId, toSpaceId);
            if (fromSpaceId != null) {
                vectorStore.onChunksChanged(fromSpaceId);
            }
            if (toSpaceId != null) {
                vectorStore.onChunksChanged(toSpaceId);
            }
            log.info("doc {} chunks moved to space {}", docId, toSpaceId);
        } catch (Exception e) {
            log.error("doc {} chunk 空间迁移失败（对账兜底）", docId, e);
        }
    }

    @Override
    public void invalidateSpace(Long spaceId) {
        try {
            int updated = wikiChunkMapper.invalidateBySpaceId(spaceId);
            vectorStore.onChunksChanged(spaceId);
            log.info("space {} chunks invalidated: {}", spaceId, updated);
        } catch (Exception e) {
            log.error("space {} 索引批量失效失败（对账兜底）", spaceId, e);
        }
    }

    @Override
    public void restoreSpace(Long spaceId) {
        try {
            // docs restored with unchanged version: their chunks flip back; the
            // rest (edited-while-deleted or missing) is covered by reconciliation
            QueryWrapper<WikiChunk> wrapper = new QueryWrapper<>();
            wrapper.eq("spaceId", spaceId).eq("status", "INVALID").eq("isDelete", 0);
            List<WikiChunk> invalid = wikiChunkMapper.selectList(wrapper);
            int restored = 0;
            for (WikiChunk chunk : invalid) {
                DocumentWiki doc = documentWikiMapper.selectById(chunk.getDocId());
                if (doc != null && (doc.getIsDelete() == null || doc.getIsDelete() == 0)
                        && doc.getContentVersion() != null
                        && doc.getContentVersion().equals(chunk.getContentVersion())) {
                    WikiChunk patch = new WikiChunk();
                    patch.setId(chunk.getId());
                    patch.setStatus(WikiChunk.STATUS_ACTIVE);
                    restored += wikiChunkMapper.updateById(patch) > 0 ? 1 : 0;
                }
            }
            vectorStore.onChunksChanged(spaceId);
            log.info("space {} chunks restored: {}", spaceId, restored);
        } catch (Exception e) {
            log.error("space {} 索引恢复失败（对账兜底）", spaceId, e);
        }
    }

    @Override
    public void deleteSpaceChunks(Long spaceId) {
        try {
            wikiChunkMapper.physicallyDeleteBySpaceId(spaceId);
            vectorStore.onChunksChanged(spaceId);
            log.info("space {} chunks physically deleted", spaceId);
        } catch (Exception e) {
            log.error("space {} chunk 物理删除失败（对账兜底）", spaceId, e);
        }
    }

    @Override
    public RagRebuildReport rebuildAll(boolean force) {
        RagRebuildReport report = new RagRebuildReport();
        QueryWrapper<DocumentWiki> wrapper = new QueryWrapper<>();
        wrapper.eq("isDelete", 0).eq("contentFormat", CONTENT_FORMAT_MARKDOWN);
        List<DocumentWiki> docs = documentWikiMapper.selectList(wrapper);
        report.setTotal(docs.size());
        if (!ragEmbeddingClient.isConfigured()) {
            report.getFailed().add("embedding 未配置（RAG_EMBEDDING_API_KEY），本次回填未执行");
            return report;
        }
        for (DocumentWiki doc : docs) {
            try {
                // force=true（换 embedding 模型后）：跳过 isUpToDate，全部文档用当前模型重嵌
                if (!force && isUpToDate(doc)) {
                    report.setSkipped(report.getSkipped() + 1);
                    continue;
                }
                // legacy rows may lack server-managed fields — normalize before indexing
                if (doc.getContentVersion() == null || StrUtil.isBlank(doc.getContentHash())) {
                    DocumentWiki patch = new DocumentWiki();
                    patch.setId(doc.getId());
                    patch.setContentVersion(doc.getContentVersion() == null ? 1 : doc.getContentVersion());
                    if (StrUtil.isNotBlank(doc.getContent())) {
                        patch.setContentHash(SecureUtil.md5(doc.getContent()));
                    }
                    documentWikiMapper.updateById(patch);
                    doc.setContentVersion(patch.getContentVersion());
                }
                doIndexDocument(doc.getId());
                report.setCreated(report.getCreated() + 1);
            } catch (Exception e) {
                log.error("rebuild failed for doc {}", doc.getId(), e);
                report.getFailed().add(doc.getTitle() + ": " + e.getMessage());
            }
        }
        log.info("rag rebuild done: total={} created={} skipped={} failed={}",
                report.getTotal(), report.getCreated(), report.getSkipped(), report.getFailed().size());
        return report;
    }

    private boolean isUpToDate(DocumentWiki doc) {
        QueryWrapper<WikiChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("docId", doc.getId()).eq("status", WikiChunk.STATUS_ACTIVE).eq("isDelete", 0);
        Long active = wikiChunkMapper.selectCount(wrapper);
        if (active == null || active == 0) {
            return false;
        }
        int version = doc.getContentVersion() == null ? 1 : doc.getContentVersion();
        wrapper.clear();
        wrapper.eq("docId", doc.getId()).eq("status", WikiChunk.STATUS_ACTIVE)
                .eq("isDelete", 0).eq("contentVersion", version);
        Long currentVersion = wikiChunkMapper.selectCount(wrapper);
        return currentVersion != null && currentVersion > 0;
    }

    @Override
    public int reconcileOrphans() {
        try {
            int fixed = wikiChunkMapper.invalidateOrphans();
            if (fixed > 0) {
                log.warn("rag reconciliation invalidated {} orphan chunks", fixed);
            }
            return fixed;
        } catch (Exception e) {
            log.error("rag reconciliation failed", e);
            return -1;
        }
    }

    @Override
    public List<WikiChunkView> listChunksOfDocument(Long docId) {
        QueryWrapper<WikiChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("docId", docId).eq("isDelete", 0)
                .orderByDesc("status").orderByAsc("contentVersion").orderByAsc("chunkIndex");
        List<WikiChunk> chunks = wikiChunkMapper.selectList(wrapper);
        List<WikiChunkView> views = new ArrayList<>(chunks.size());
        for (WikiChunk chunk : chunks) {
            String preview = chunk.getChunkText() == null ? "" : chunk.getChunkText();
            if (preview.length() > TEXT_PREVIEW_LENGTH) {
                preview = preview.substring(0, TEXT_PREVIEW_LENGTH) + "…";
            }
            views.add(new WikiChunkView(chunk.getId(), chunk.getDocId(), chunk.getChunkIndex(),
                    chunk.getChunkHeading(), chunk.getDocNumber(), chunk.getStatus(),
                    chunk.getContentVersion(), preview));
        }
        return views;
    }
}
