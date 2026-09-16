package com.et.cloud.rag;

import cn.hutool.crypto.SecureUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownChunkerTest {

    /**
     * Guards the Chinese baseline. The digest was captured from the implementation before the
     * parameters moved into {@link ChunkerProfile}, so any drift in caps, overlap, sentence
     * boundaries or section ordering fails here instead of silently invalidating the 216-document
     * comparison baseline.
     */
    @Test
    void chineseChunkingIsUnchangedByTheProfileRefactor() {
        List<MarkdownChunk> chunks = MarkdownChunker.chunk(ChineseChunkFixture.CONTENT, ChunkerProfile.ZH);

        StringBuilder joined = new StringBuilder();
        for (MarkdownChunk chunk : chunks) {
            joined.append(chunk.getIndex()).append('\t')
                    .append(chunk.getHeadingPath()).append('\t')
                    .append(chunk.getText()).append('\n');
        }

        assertEquals(7, chunks.size(), "chunk count must match the pre-profile implementation");
        assertEquals("59893205bd85247b8586f58666197bde361f3e8b23d5c1f166080d98be40c124",
                SecureUtil.sha256(joined.toString()),
                "Chinese chunk sequence drifted — the existing 2061-chunk baseline would no longer hold");
    }

    @Test
    void documentWithoutHeadingsIsSplitByLength() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("第").append(i).append("段：这是一段普通的政策说明文字，用于测试无标题文档的切片行为。");
            sb.append("\n\n");
        }
        List<MarkdownChunk> chunks = MarkdownChunker.chunk(sb.toString(), ChunkerProfile.ZH);
        assertTrue(chunks.size() >= 2, "oversized flat doc must be split, got " + chunks.size());
        for (MarkdownChunk chunk : chunks) {
            assertTrue(chunk.getText().length() <= ChunkerProfile.ZH.getMaxChunkLength() + ChunkerProfile.ZH.getOverlapLength() + 2,
                    "chunk must respect cap (plus overlap), got " + chunk.getText().length());
        }
    }

    @Test
    void sectionsFollowHeadingHierarchy() {
        String md = "# 重庆市政策文件\n\n" +
                "文件总体说明。\n\n" +
                "## 三、补助标准\n\n" +
                "对符合条件的对象按月发放补助。\n\n" +
                "### (二)发放方式\n\n" +
                "通过银行代发到个人账户。\n\n" +
                "## 四、监督管理\n\n" +
                "由民政部门负责监督。\n";
        List<MarkdownChunk> chunks = MarkdownChunker.chunk(md, ChunkerProfile.ZH);
        assertEquals(1, chunks.size(), "tiny sections merge into neighbours");
        assertTrue(chunks.get(0).getText().contains("三、补助标准"));
        assertTrue(chunks.get(0).getText().contains("监督"));
    }

    @Test
    void headingPathRecordsHierarchy() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            body.append("本节内容详细规定了若干条具体措施与执行口径，内容较长用于避免与相邻节合并。");
        }
        String md = "# 标题一\n\n## 第一节 政策目标\n\n" + body + "\n\n## 第二节 保障措施\n\n" + body;
        List<MarkdownChunk> chunks = MarkdownChunker.chunk(md, ChunkerProfile.ZH);
        assertTrue(chunks.size() >= 2);
        boolean found = chunks.stream().anyMatch(c -> "标题一 > 第一节 政策目标".equals(c.getHeadingPath()));
        assertTrue(found, "heading path must include parent, got paths: "
                + chunks.stream().map(MarkdownChunk::getHeadingPath).distinct().collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void oversizedSectionIsSplitByParagraph() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            body.append("第").append(i).append("条 本条款规定了一系列具体的执行细则和操作要求，确保政策落地。");
            body.append("\n\n");
        }
        String md = "# 办法\n\n## 第二章 实施细则\n\n" + body;
        List<MarkdownChunk> chunks = MarkdownChunker.chunk(md, ChunkerProfile.ZH);
        assertTrue(chunks.size() >= 2, "oversized section must split, got " + chunks.size());
        for (MarkdownChunk chunk : chunks) {
            assertTrue(chunk.getText().length() <= ChunkerProfile.ZH.getMaxChunkLength() + ChunkerProfile.ZH.getOverlapLength() + 2);
            assertEquals("办法 > 第二章 实施细则", chunk.getHeadingPath());
        }
    }

    @Test
    void adjacentChunksShareOverlap() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            body.append("条款内容").append(i).append("：本段规定了一些执行标准与申请流程细节，占位文本。");
            body.append("\n\n");
        }
        List<MarkdownChunk> chunks = MarkdownChunker.chunk(body.toString(), ChunkerProfile.ZH);
        assertTrue(chunks.size() >= 2);
        // overlap carried from previous chunk must appear at the head of the next chunk
        String prevTail80 = tail(chunks.get(0).getText());
        String nextHead = chunks.get(1).getText().substring(0, Math.min(10, chunks.get(1).getText().length()));
        assertTrue(prevTail80.contains(nextHead) || chunks.get(1).getText().startsWith(
                        prevTail80.substring(Math.min(prevTail80.length(), 10))),
                "next chunk should start with carried-over tail text");
    }

    private static String tail(String text) {
        return text.substring(Math.max(0, text.length() - ChunkerProfile.ZH.getOverlapLength()));
    }

    @Test
    void frontMatterStaysWithFirstChunk() {
        String md = "---\ntitle: \"测试文档\"\nfileNum: \"渝府发〔2026〕8号\"\n---\n\n" +
                "正文第一段说明。\n\n## 第一章 总则\n\n正文第二段。\n";
        List<MarkdownChunk> chunks = MarkdownChunker.chunk(md, ChunkerProfile.ZH);
        assertTrue(chunks.size() >= 1);
        assertTrue(chunks.get(0).getText().contains("fileNum"), "front matter rides with the first chunk");
    }

    @Test
    void docNumberExtracted() {
        String content = "渝府办发〔2026〕24号 各区县人民政府：";
        assertEquals("渝府办发〔2026〕24号", MarkdownChunker.extractDocNumber(content));
    }

    @Test
    void docNumberMissingReturnsNull() {
        assertNull(MarkdownChunker.extractDocNumber("本文没有文号"));
        assertNull(MarkdownChunker.extractDocNumber(null));
        assertNull(MarkdownChunker.extractDocNumber(""));
    }

    @Test
    void docNumberPatternVariants() {
        assertNotNull(MarkdownChunker.extractDocNumber("渝府发〔2025〕17号"));
        assertNotNull(MarkdownChunker.extractDocNumber("发改价格〔2024〕1234号"));
        assertNull(MarkdownChunker.extractDocNumber("渝府发[2026]24号"), "half-width brackets must not match");
    }

    @Test
    void blankContentReturnsEmpty() {
        assertEquals(0, MarkdownChunker.chunk(null, ChunkerProfile.ZH).size());
        assertEquals(0, MarkdownChunker.chunk("   \n  ", ChunkerProfile.ZH).size());
    }

    @Test
    void chunkIndexesAreSequential() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            body.append("内容").append(i).append("：占位文本用于产生多个切片，保持足够长度以避免合并。");
            body.append("\n\n");
        }
        List<MarkdownChunk> chunks = MarkdownChunker.chunk(body.toString(), ChunkerProfile.ZH);
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getIndex());
        }
    }
}
