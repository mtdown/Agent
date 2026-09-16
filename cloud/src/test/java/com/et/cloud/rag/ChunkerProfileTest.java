package com.et.cloud.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parameter selection and the language-specific split behaviour: Chinese values must stay exactly as
 * they were, English must stop cutting inside words.
 */
class ChunkerProfileTest {

    @Test
    void chineseParametersAreUnchanged() {
        assertEquals(600, ChunkerProfile.ZH.getMaxChunkLength());
        assertEquals(100, ChunkerProfile.ZH.getMinChunkLength());
        assertEquals(80, ChunkerProfile.ZH.getOverlapLength());
        assertEquals("。；", ChunkerProfile.ZH.getSentenceBoundaries());
        assertEquals("zh", ChunkerProfile.ZH.getName());
    }

    @Test
    void englishParametersUseTheLongerCapAndOverlap() {
        assertEquals(1800, ChunkerProfile.EN.getMaxChunkLength());
        assertEquals(100, ChunkerProfile.EN.getMinChunkLength());
        assertEquals(100, ChunkerProfile.EN.getOverlapLength());
        assertEquals(".!?;", ChunkerProfile.EN.getSentenceBoundaries());
        assertEquals("en", ChunkerProfile.EN.getName());
    }

    @Test
    void englishChunksRespectTheCapAndOverlap() {
        StringBuilder md = new StringBuilder("# News\n\n");
        for (int i = 0; i < 30; i++) {
            md.append("The committee said the measure would take effect once the review period ends. ");
        }

        List<MarkdownChunk> chunks = MarkdownChunker.chunk(md.toString(), ChunkerProfile.EN);

        assertTrue(chunks.size() >= 2, "a paragraph over the cap must be split, got " + chunks.size());
        for (MarkdownChunk chunk : chunks) {
            assertTrue(chunk.getText().length()
                            <= ChunkerProfile.EN.getMaxChunkLength() + ChunkerProfile.EN.getOverlapLength() + 2,
                    "chunk must respect cap plus overlap, got " + chunk.getText().length());
        }
    }

    @Test
    void englishCutsAtSentenceEndsNeverInsideAWord() {
        StringBuilder md = new StringBuilder("# News\n\n");
        for (int i = 0; i < 40; i++) {
            md.append("The committee said the measure would take effect once the review period ends. ");
        }

        List<MarkdownChunk> chunks = MarkdownChunker.chunk(md.toString(), ChunkerProfile.EN);

        assertTrue(chunks.size() >= 2);
        for (MarkdownChunk chunk : chunks) {
            assertTrue(chunk.getText().strip().endsWith("."),
                    "every window must close on a sentence end, got: "
                            + chunk.getText().strip().substring(Math.max(0, chunk.getText().strip().length() - 40)));
        }
    }

    @Test
    void englishFallsBackToWordBoundariesAndNeverSplitsAWord() {
        // uniform 10-character words: cutting at the raw 1800 cap lands inside word 164, so this
        // only passes when the cut is moved to a space
        String word = "abcdefghij";
        StringBuilder md = new StringBuilder("# News\n\n");
        for (int i = 0; i < 400; i++) {
            md.append(word).append(' ');
        }

        List<MarkdownChunk> chunks = MarkdownChunker.chunk(md.toString(), ChunkerProfile.EN);

        assertTrue(chunks.size() >= 2, "expected several windows, got " + chunks.size());
        for (MarkdownChunk chunk : chunks) {
            assertTrue(chunk.getText().strip().endsWith(word),
                    "window must end on a whole word, got: "
                            + chunk.getText().strip().substring(Math.max(0, chunk.getText().strip().length() - 20)));
        }
    }

    @Test
    void latinSentenceEndsIgnoreDecimalsAndAbbreviations() {
        assertEquals(-1, ChunkerProfile.EN.lastSentenceEnd("He met Dr. Smith", 0, 16),
                "an abbreviation period must not end a sentence");
        assertEquals(-1, ChunkerProfile.EN.lastSentenceEnd("Nov. prices rose", 0, 16),
                "a month abbreviation must not end a sentence");
        assertEquals(-1, ChunkerProfile.EN.lastSentenceEnd("The U.S. economy grew", 0, 21),
                "an initialism must not end a sentence");
        assertEquals(-1, ChunkerProfile.EN.lastSentenceEnd("Acme Inc. reported", 0, 18),
                "a company suffix must not end a sentence");
        assertEquals(-1, ChunkerProfile.EN.lastSentenceEnd("It cost $US90.39 total", 0, 21),
                "a decimal point must not end a sentence");
        assertEquals(-1, ChunkerProfile.EN.lastSentenceEnd("It rose 1.5 percent", 0, 19),
                "a decimal point in a plain number must not end a sentence");
    }

    @Test
    void latinSentenceEndsAreFoundOnRealSentenceEndings() {
        String sentences = "Sales grew. Costs fell. Margins held.";
        assertEquals(sentences.length() - 1,
                ChunkerProfile.EN.lastSentenceEnd(sentences, 0, sentences.length() - 1));
        assertEquals(sentences.indexOf('.'),
                ChunkerProfile.EN.lastSentenceEnd(sentences, 0, sentences.indexOf('.')));

        String question = "Why did it fall? Nobody said";
        assertEquals(question.indexOf('?'),
                ChunkerProfile.EN.lastSentenceEnd(question, 0, question.length() - 1));

        String exclamation = "Prices jumped! Nobody said";
        assertEquals(exclamation.indexOf('!'),
                ChunkerProfile.EN.lastSentenceEnd(exclamation, 0, exclamation.length() - 1));

        String clause = "It fell; nobody said";
        assertEquals(clause.indexOf(';'),
                ChunkerProfile.EN.lastSentenceEnd(clause, 0, clause.length() - 1));
    }

    @Test
    void chineseHasNoWordBoundaryFallback() {
        String chinese = "本办法适用于全市范围";
        assertEquals(-1, ChunkerProfile.ZH.lastWordBoundary(chinese, 0, chinese.length()));
        // the search window is inclusive, matching the historical lastIndexOf('。', end) behaviour
        assertEquals(chinese.length(),
                ChunkerProfile.ZH.lastSentenceEnd(chinese + "。", 0, chinese.length()));
    }

    @Test
    void languageDetectionCoversChineseEnglishAndMixedContent() {
        assertEquals(ChunkerProfile.ZH, ChunkerProfile.forContent("重庆市困难群众救助补助资金管理办法"));
        assertEquals(ChunkerProfile.EN, ChunkerProfile.forContent("Multi hop retrieval over English news corpora"));
        // mixed script: whichever script holds the majority of letters decides
        assertEquals(ChunkerProfile.ZH, ChunkerProfile.forContent("RAG 检索增强生成在政务问答中的应用"));
        assertEquals(ChunkerProfile.EN, ChunkerProfile.forContent("RAG retrieval augmented generation for policy QA"));
        // no letters at all: fall back to the long-standing Chinese behaviour
        assertEquals(ChunkerProfile.ZH, ChunkerProfile.forContent("12345 --- 67890"));
        assertEquals(ChunkerProfile.ZH, ChunkerProfile.forContent(null));
    }

    @Test
    void detectionIsDeterministicAndIgnoresModelState() {
        String content = "RAG 检索增强生成在政务问答中的应用";
        assertSame(ChunkerProfile.forContent(content), ChunkerProfile.forContent(content));
        assertSame(ChunkerProfile.ZH, ChunkerProfile.forContent(content));
    }

    @Test
    void storedLanguageOverridesDetection() {
        String chineseHeavy = "重庆市困难群众救助补助资金管理办法";
        assertEquals(ChunkerProfile.ZH, ChunkerProfile.forContent(chineseHeavy));
        assertSame(ChunkerProfile.EN, ChunkerProfile.resolve(chineseHeavy, "en"),
                "a recorded language must win, otherwise a rebuild could chunk differently");
        assertSame(ChunkerProfile.ZH, ChunkerProfile.resolve("plain english text", "zh"));
        assertSame(ChunkerProfile.EN, ChunkerProfile.resolve("plain english text", null));
        assertSame(ChunkerProfile.EN, ChunkerProfile.byName("EN"));
        assertSame(ChunkerProfile.ZH, ChunkerProfile.byName(" zh "));
        assertNull(ChunkerProfile.byName("klingon"));
        assertNull(ChunkerProfile.byName(null));
    }
}
