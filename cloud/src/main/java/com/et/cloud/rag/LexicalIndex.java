package com.et.cloud.rag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.WikiChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lexical (BM25) retrieval channel over the same authorized corpus as the
 * vector store.
 *
 * <p>Why this exists: the dense channel alone leaves gold chunks ranked well
 * outside top-K on this corpus. Measured on the 423-question eval set
 * (399 scored), a perfect re-ranker over the dense candidate pool reaches
 * recall@6 = 0.8148 at depth 50 and 0.8532 at depth 100 — i.e. the candidate
 * pool itself, not the ranking step, caps the achievable recall. Adding this
 * lexical channel lifts the same figures to 0.8600 / 0.9061.
 *
 * <p>Tokenization is CJK bigram based, so no external segmenter and no MySQL
 * ngram parser behaviour is involved. The index is built lazily per space from
 * the chunk table, exactly like {@link InMemoryCosineVectorStore}, and is
 * invalidated through {@link #onChunksChanged(Long)}.
 */
@Component
@Slf4j
public class LexicalIndex {

    /** Chunk text is indexed up to this length, matching the vector store preview. */
    private static final int MAX_TEXT_PREVIEW = 2000;

    @Resource
    private WikiChunkMapper wikiChunkMapper;

    @Resource
    private RagProperties ragProperties;

    /** spaceId -> immutable snapshot (copy-on-write replace, same policy as the vector store). */
    private final Map<Long, Snapshot> cache = new ConcurrentHashMap<>();

    /**
     * Returns the top-K chunks by BM25 score within the given spaces. Callers
     * pass the already permission-filtered effective space set; this channel
     * never widens it.
     */
    public List<ChunkHit> search(String query, Set<Long> spaceIds, int topK) {
        List<ChunkHit> hits = new ArrayList<>();
        if (query == null || query.isBlank() || spaceIds == null || spaceIds.isEmpty() || topK <= 0) {
            return hits;
        }
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return hits;
        }
        Set<String> unique = new LinkedHashSet<>(terms);
        RagProperties.Lexical cfg = ragProperties.getRetrieval().getLexical();
        double k1 = cfg.getK1();
        double b = cfg.getB();

        for (Long spaceId : spaceIds) {
            Snapshot snapshot = cache.computeIfAbsent(spaceId, this::loadSpace);
            if (snapshot.isEmpty()) {
                continue;
            }
            Map<Long, Double> scores = new HashMap<>();
            for (String term : unique) {
                Map<Long, Integer> posting = snapshot.postings.get(term);
                if (posting == null) {
                    continue;
                }
                double idf = Math.log(1.0 + (snapshot.docCount - posting.size() + 0.5) / (posting.size() + 0.5));
                for (Map.Entry<Long, Integer> e : posting.entrySet()) {
                    long chunkId = e.getKey();
                    int tf = e.getValue();
                    double dl = snapshot.docLength.getOrDefault(chunkId, 0);
                    double denom = tf + k1 * (1.0 - b + b * dl / snapshot.avgDocLength);
                    if (denom <= 0) {
                        continue;
                    }
                    scores.merge(chunkId, idf * tf * (k1 + 1.0) / denom, Double::sum);
                }
            }
            for (Map.Entry<Long, Double> e : scores.entrySet()) {
                Snapshot.Entry entry = snapshot.entries.get(e.getKey());
                if (entry == null) {
                    continue;
                }
                hits.add(new ChunkHit(entry.chunkId, entry.docId, entry.spaceId, entry.chunkIndex,
                        entry.chunkHeading, entry.chunkText, entry.docTitle, entry.docNumber, e.getValue()));
            }
        }
        hits.sort(Comparator.comparingDouble(ChunkHit::getScore).reversed());
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    /**
     * Drops the cached snapshot of a space so the next query rebuilds it.
     * Called from the same lifecycle hooks that notify the vector store.
     */
    public void onChunksChanged(Long spaceId) {
        if (spaceId == null) {
            return;
        }
        cache.remove(spaceId);
    }

    /**
     * CJK runs are expanded into adjacent character bigrams (single-character
     * runs stay as-is); ASCII letter/digit runs are kept as whole words. This
     * keeps Chinese recall without a segmenter and degrades gracefully for the
     * English corpus.
     */
    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        String lower = text.toLowerCase();
        int i = 0;
        int n = lower.length();
        while (i < n) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                int start = i;
                while (i < n && isCjk(lower.charAt(i))) {
                    i++;
                }
                int len = i - start;
                if (len == 1) {
                    out.add(lower.substring(start, i));
                } else {
                    for (int j = start; j < i - 1; j++) {
                        out.add(lower.substring(j, j + 2));
                    }
                }
            } else if (Character.isLetterOrDigit(c)) {
                int start = i;
                while (i < n && Character.isLetterOrDigit(lower.charAt(i)) && !isCjk(lower.charAt(i))) {
                    i++;
                }
                out.add(lower.substring(start, i));
            } else {
                i++;
            }
        }
        return out;
    }

    private static boolean isCjk(char c) {
        return c >= '\u4e00' && c <= '\u9fa5';
    }

    private Snapshot loadSpace(Long spaceId) {
        QueryWrapper<WikiChunk> wrapper = new QueryWrapper<>();
        wrapper.eq("spaceId", spaceId).eq("status", WikiChunk.STATUS_ACTIVE).eq("isDelete", 0);
        List<WikiChunk> chunks = wikiChunkMapper.selectList(wrapper);
        Map<String, Map<Long, Integer>> postings = new HashMap<>();
        Map<Long, Integer> docLength = new HashMap<>();
        Map<Long, Snapshot.Entry> entries = new HashMap<>();
        long totalLength = 0L;
        for (WikiChunk chunk : chunks) {
            String text = chunk.getChunkText();
            if (text != null && text.length() > MAX_TEXT_PREVIEW) {
                text = text.substring(0, MAX_TEXT_PREVIEW);
            }
            List<String> terms = tokenize(text);
            if (terms.isEmpty()) {
                continue;
            }
            Map<String, Integer> tf = new HashMap<>();
            for (String term : terms) {
                tf.merge(term, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                postings.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(chunk.getId(), e.getValue());
            }
            docLength.put(chunk.getId(), terms.size());
            totalLength += terms.size();
            entries.put(chunk.getId(), new Snapshot.Entry(chunk.getId(), chunk.getDocId(), chunk.getSpaceId(),
                    chunk.getChunkIndex(), chunk.getChunkHeading(), text, chunk.getDocTitle(), chunk.getDocNumber()));
        }
        double avg = docLength.isEmpty() ? 0.0 : (double) totalLength / docLength.size();
        log.debug("lexical index loaded space {}: {} chunks, {} terms", spaceId, entries.size(), postings.size());
        return new Snapshot(postings, docLength, entries, entries.size(), avg);
    }

    /** Immutable per-space snapshot of the inverted index. */
    private static final class Snapshot {

        private final Map<String, Map<Long, Integer>> postings;
        private final Map<Long, Integer> docLength;
        private final Map<Long, Entry> entries;
        private final int docCount;
        private final double avgDocLength;

        private Snapshot(Map<String, Map<Long, Integer>> postings, Map<Long, Integer> docLength,
                         Map<Long, Entry> entries, int docCount, double avgDocLength) {
            this.postings = postings;
            this.docLength = docLength;
            this.entries = entries;
            this.docCount = docCount;
            this.avgDocLength = avgDocLength <= 0 ? 1.0 : avgDocLength;
        }

        private boolean isEmpty() {
            return docCount == 0;
        }

        private static final class Entry {
            private final Long chunkId;
            private final Long docId;
            private final Long spaceId;
            private final Integer chunkIndex;
            private final String chunkHeading;
            private final String chunkText;
            private final String docTitle;
            private final String docNumber;

            private Entry(Long chunkId, Long docId, Long spaceId, Integer chunkIndex, String chunkHeading,
                          String chunkText, String docTitle, String docNumber) {
                this.chunkId = chunkId;
                this.docId = docId;
                this.spaceId = spaceId;
                this.chunkIndex = chunkIndex;
                this.chunkHeading = chunkHeading;
                this.chunkText = chunkText;
                this.docTitle = docTitle;
                this.docNumber = docNumber;
            }
        }
    }
}
