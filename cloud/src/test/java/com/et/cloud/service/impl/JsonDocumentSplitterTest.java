package com.et.cloud.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.model.dto.ImportedWikiDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON corpus splitting: entry mapping, field aliases, metadata preservation and the guards that
 * keep a broken file from importing half a corpus.
 */
class JsonDocumentSplitterTest {

    @Test
    void everyEntryBecomesOneDocument() {
        List<ImportedWikiDocument> documents = split("[{\"title\":\"A\",\"content\":\"one\"},"
                + "{\"title\":\"B\",\"content\":\"two\"},{\"title\":\"C\",\"content\":\"three\"}]");

        assertEquals(3, documents.size());
        assertEquals("A", documents.get(0).getTitle());
        assertEquals("three", documents.get(2).getContent());
    }

    @Test
    void wrapperShapeIsAcceptedAndOtherTopLevelFieldsAreIgnored() {
        List<ImportedWikiDocument> documents = split(
                "{\"meta\":{\"version\":1},\"documents\":[{\"title\":\"W\",\"content\":\"body\"}],\"other\":2}");

        assertEquals(1, documents.size());
        assertEquals("W", documents.get(0).getTitle());
    }

    @Test
    void fieldAliasesAreAcceptedForTitleAndContent() {
        List<ImportedWikiDocument> documents = split("[{\"title\":\"T\",\"body\":\"from body\"},"
                + "{\"name\":\"N\",\"text\":\"from text\"}]");

        assertEquals("T", documents.get(0).getTitle());
        assertEquals("from body", documents.get(0).getContent());
        assertEquals("N", documents.get(1).getTitle());
        assertEquals("from text", documents.get(1).getContent());
    }

    @Test
    void contentAliasesAreResolvedByFixedPriority() {
        List<ImportedWikiDocument> documents = split(
                "[{\"title\":\"T\",\"text\":\"third\",\"body\":\"second\",\"content\":\"first\"}]");

        assertEquals("first", documents.get(0).getContent());
        // the unused aliases stay traceable in the metadata record
        assertTrue(documents.get(0).getMetadataJson().contains("\"body\""),
                documents.get(0).getMetadataJson());
        assertTrue(documents.get(0).getMetadataJson().contains("\"text\""),
                documents.get(0).getMetadataJson());
    }

    @Test
    void nonTextContentValueIsNotCoercedIntoContent() {
        List<ImportedWikiDocument> documents = split("[{\"title\":\"T\",\"content\":{\"nested\":1}}]");

        assertTrue(StrUtil.isBlank(documents.get(0).getContent()));
        assertTrue(documents.get(0).getMetadataJson().contains("\"nested\""),
                documents.get(0).getMetadataJson());
    }

    @Test
    void entryWithoutTitleGetsAFallbackTitle() {
        List<ImportedWikiDocument> fromUrl = split("[{\"url\":\"https://news.test/policy/report-2026.html\",\"content\":\"c\"}]");
        assertEquals("report-2026", fromUrl.get(0).getTitle());

        List<ImportedWikiDocument> fromIndex = split("[{\"content\":\"c\"}]");
        assertEquals("条目 1", fromIndex.get(0).getTitle());
    }

    @Test
    void entryWithoutContentIsStillEmittedSoTheCallerCanReportIt() {
        List<ImportedWikiDocument> documents = split("[{\"title\":\"A\",\"content\":\"kept\"},"
                + "{\"title\":\"B\"},{\"title\":\"C\",\"content\":\"also kept\"}]");

        assertEquals(3, documents.size(), "a bad entry must not drop the surrounding entries");
        assertTrue(StrUtil.isBlank(documents.get(1).getContent()));
        assertEquals("kept", documents.get(0).getContent());
        assertEquals("also kept", documents.get(2).getContent());
    }

    @Test
    void metadataKeepsEveryOriginalFieldExceptTheContentField() {
        ImportedWikiDocument document = split("[{\"title\":\"T\",\"body\":\"the text\","
                + "\"source\":\"mashable\",\"published_at\":\"2023-06-01\",\"category\":\"tech\","
                + "\"author\":\"Jane\",\"url\":\"https://news.test/a\"}]").get(0);

        String metadata = document.getMetadataJson();
        assertTrue(metadata.contains("\"source\":\"mashable\""), metadata);
        assertTrue(metadata.contains("\"published_at\":\"2023-06-01\""), metadata);
        assertTrue(metadata.contains("\"category\":\"tech\""), metadata);
        assertTrue(metadata.contains("\"author\":\"Jane\""), metadata);
        assertTrue(metadata.contains("\"url\":\"https://news.test/a\""), metadata);
        assertTrue(metadata.contains("\"title\":\"T\""), metadata);
        assertTrue(metadata.contains("\"_entryIndex\":0"), metadata);
        assertFalse(metadata.contains("the text"), "the content must not be duplicated into metadata: " + metadata);
        assertDoesNotThrow(() -> JSONUtil.parseObj(metadata));
    }

    @Test
    void entryOrderIsRecordedInMetadata() {
        List<ImportedWikiDocument> documents = split("[{\"title\":\"A\",\"content\":\"a\"},"
                + "{\"title\":\"B\",\"content\":\"b\"}]");

        assertTrue(documents.get(0).getMetadataJson().contains("\"_entryIndex\":0"));
        assertTrue(documents.get(1).getMetadataJson().contains("\"_entryIndex\":1"));
    }

    @Test
    void urlIsWrittenToBothSourceUrlAndMetadata() {
        ImportedWikiDocument document = split(
                "[{\"title\":\"T\",\"content\":\"c\",\"url\":\"https://news.test/a\"}]").get(0);

        assertEquals("https://news.test/a", document.getSourceUrl());
        assertTrue(document.getMetadataJson().contains("\"url\":\"https://news.test/a\""));
    }

    @Test
    void entryWithoutUrlGetsNoSourceUrl() {
        assertNull(split("[{\"title\":\"T\",\"content\":\"c\"}]").get(0).getSourceUrl());
    }

    @Test
    void contentFormatAndSourceTypeAreFixed() {
        List<ImportedWikiDocument> documents = split("[{\"title\":\"T\",\"content\":\"c\"}]");

        // plain would be silently skipped by the rebuild path, which only re-indexes markdown
        assertEquals("markdown", documents.get(0).getContentFormat());
        assertEquals("IMPORT", documents.get(0).getSourceType());
    }

    @Test
    void detectedLanguageIsWrittenIntoMetadata() {
        ImportedWikiDocument english = split("[{\"title\":\"T\",\"content\":\""
                + "The committee said the measure would take effect after the review period ends.\"}]").get(0);
        assertTrue(english.getMetadataJson().contains("\"language\":\"en\""), english.getMetadataJson());

        ImportedWikiDocument chinese = split("[{\"title\":\"T\",\"content\":\"重庆市困难群众救助补助资金管理办法\"}]").get(0);
        assertTrue(chinese.getMetadataJson().contains("\"language\":\"zh\""), chinese.getMetadataJson());
    }

    @Test
    void oversizedMetadataIsTrimmedByPriorityAndStaysValidJson() {
        String big = repeat("x", 900);
        ImportedWikiDocument document = split("[{\"title\":\"T\",\"content\":\"重庆市困难群众救助补助资金管理办法\","
                + "\"source\":\"" + big + "\",\"published_at\":\"" + big + "\",\"url\":\"" + big + "\","
                + "\"category\":\"" + big + "\",\"author\":\"" + big + "\",\"extra\":\"" + big + "\"}]").get(0);

        String metadata = document.getMetadataJson();
        assertTrue(metadata.length() <= JsonDocumentSplitter.MAX_METADATA_LENGTH,
                "trimmed metadata must fit the column limit, got " + metadata.length());
        assertDoesNotThrow(() -> JSONUtil.parseObj(metadata), metadata);
        assertFalse(metadata.contains("\"source\""), "source is dropped first: " + metadata);
        assertFalse(metadata.contains("\"url\""), metadata);
        // the record still has to identify its entry and the profile it was chunked with
        assertTrue(metadata.contains("\"_entryIndex\":0"), metadata);
        assertTrue(metadata.contains("\"language\":\"zh\""), metadata);
    }

    @Test
    void metadataThatCannotBeTrimmedFallsBackToAMarker() {
        ImportedWikiDocument document = split(
                "[{\"title\":\"" + repeat("t", 3000) + "\",\"content\":\"c\"}]").get(0);

        assertEquals("{\"truncated\":true}", document.getMetadataJson());
        assertEquals(128, document.getTitle().length(), "the entry still imports with a truncated title");
    }

    @Test
    void longTitleIsTruncatedWhileTheFullTitleStaysInMetadata() {
        String longTitle = repeat("长", 200);
        ImportedWikiDocument document = split("[{\"title\":\"" + longTitle + "\",\"content\":\"c\"}]").get(0);

        assertEquals(128, document.getTitle().length());
        assertTrue(document.getMetadataJson().contains(longTitle),
                "the document column truncates, so metadata is the only place the full title survives");
    }

    @Test
    void invalidJsonIsRejectedBeforeAnyEntryIsHandedOut() {
        List<ImportedWikiDocument> seen = new ArrayList<>();

        assertThrows(BusinessException.class, () -> JsonDocumentSplitter.split(
                "[{\"title\":\"a\",\"content\":\"x\"},{\"title\":\"b\"", seen::add));
        assertTrue(seen.isEmpty(), "a partially readable file must not import a partial corpus");
    }

    @Test
    void notJsonAtAllIsRejected() {
        assertThrows(BusinessException.class, () -> split("not json at all"));
        assertThrows(BusinessException.class, () -> split(""));
    }

    @Test
    void emptyEntrySetIsRejected() {
        assertEquals(0, JsonDocumentSplitter.split("[]", document -> {
        }));
    }

    @Test
    void unsupportedTopLevelShapeIsRejected() {
        assertThrows(BusinessException.class, () -> split("\"just a string\""));
        assertThrows(BusinessException.class, () -> split("{\"items\":[]}"));
    }

    private List<ImportedWikiDocument> split(String json) {
        List<ImportedWikiDocument> documents = new ArrayList<>();
        JsonDocumentSplitter.split(json, documents::add);
        return documents;
    }

    private static String repeat(String unit, int times) {
        StringBuilder sb = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }
}
