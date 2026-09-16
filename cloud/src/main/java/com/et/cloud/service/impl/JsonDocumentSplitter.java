package com.et.cloud.service.impl;

import cn.hutool.core.util.StrUtil;
import com.et.cloud.exception.BusinessException;
import com.et.cloud.exception.ErrorCode;
import com.et.cloud.model.dto.ImportedWikiDocument;
import com.et.cloud.rag.ChunkerProfile;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Splits one JSON corpus file into one {@link ImportedWikiDocument} per entry.
 *
 * <p>Pure utility, the JSON counterpart of {@link WebPageMarkdownCleaner}: it turns an upstream
 * corpus into the object the existing import pipeline already understands, so persistence, per-item
 * results, permission checks, cache eviction and the asynchronous chunk/embedding flow are all
 * reused unchanged by the caller.
 *
 * <h3>Accepted shapes</h3>
 * <ul>
 *   <li>top level array: {@code [ {...}, {...} ]}</li>
 *   <li>object wrapper: {@code {"documents": [ {...} ]}} — every other top level field is skipped</li>
 * </ul>
 *
 * <h3>Field convention (fixed, no per-dataset mapping configuration)</h3>
 * <ul>
 *   <li>title: {@code title}, then {@code name}</li>
 *   <li>content: {@code content}, then {@code body}, then {@code text}</li>
 * </ul>
 * Every other entry field is preserved as one metadata record, including the entry's complete
 * original title. Entries are read one element at a time, so the corpus is never materialised as a
 * JSON tree.
 */
public final class JsonDocumentSplitter {

    /**
     * Mirrors {@code DocumentWikiServiceImpl.MAX_TITLE_LENGTH}. Exceeding it fails the whole entry,
     * so the splitter truncates while the complete title stays in the metadata record.
     */
    public static final int MAX_TITLE_LENGTH = 128;

    /**
     * Mirrors {@code DocumentWikiServiceImpl.MAX_METADATA_LENGTH}.
     */
    public static final int MAX_METADATA_LENGTH = 2048;

    /**
     * Object key holding the entry array in the wrapper shape.
     */
    public static final String WRAPPER_ARRAY_KEY = "documents";

    /**
     * Metadata key recording the entry's position in the file, so results stay traceable even when
     * the caller's per-item identifier is not unique.
     */
    public static final String ENTRY_INDEX_KEY = "_entryIndex";

    /**
     * Content format written for every entry. Must stay {@code markdown}: the rebuild flow only
     * re-indexes markdown documents, so {@code plain} would be skipped silently later on.
     */
    public static final String CONTENT_FORMAT = "markdown";

    public static final String SOURCE_TYPE = "IMPORT";

    private static final List<String> TITLE_KEYS = Arrays.asList("title", "name");

    private static final List<String> CONTENT_KEYS = Arrays.asList("content", "body", "text");

    private static final String TITLE_KEY = "title";

    private static final String URL_KEY = "url";

    /**
     * Language selected for the entry, read back by the indexing side. Not an entry field, so it is
     * written explicitly rather than carried over from the source record.
     */
    private static final String LANGUAGE_KEY = "language";

    /**
     * Metadata keys given up first when the record does not fit, least useful for traceability first.
     */
    private static final List<String> METADATA_DROP_ORDER =
            Arrays.asList("source", "published_at", "url", "category", "author");

    /**
     * Never dropped while trimming: the record must still identify the entry it came from and the
     * language it was chunked with, otherwise a rebuild could re-detect a different profile.
     */
    private static final List<String> METADATA_KEEP_LAST =
            Arrays.asList(TITLE_KEY, ENTRY_INDEX_KEY, LANGUAGE_KEY);

    private static final String TRUNCATED_METADATA = "{\"truncated\":true}";

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(JSON_FACTORY);

    private static final TypeReference<LinkedHashMap<String, Object>> FIELD_MAP =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    private JsonDocumentSplitter() {
    }

    /**
     * Reads every entry of {@code json} and hands one {@link ImportedWikiDocument} per entry to
     * {@code consumer} as soon as that entry has been read.
     *
     * <p>An entry without usable content is still emitted (with empty content) so the caller's
     * per-item handling reports it as a failure without disturbing the remaining entries. The
     * document is syntax-validated first, so a file that cannot be parsed never imports partially.
     *
     * @return number of entries read from the file
     */
    public static int split(byte[] json, Consumer<ImportedWikiDocument> consumer) {
        if (json == null || json.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容不能为空");
        }
        if (consumer == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缺少条目接收器");
        }
        validateSyntax(json);
        return streamEntries(json, consumer);
    }

    /**
     * Reads entries from a JSON string.
     */
    public static int split(String json, Consumer<ImportedWikiDocument> consumer) {
        return split(json == null ? null : json.getBytes(StandardCharsets.UTF_8), consumer);
    }

    /**
     * Walks the document once without building anything, so malformed JSON is rejected before a
     * single document is created.
     */
    private static void validateSyntax(byte[] json) {
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            while (parser.nextToken() != null) {
                // token walk only: the parser itself raises the failure we care about
            }
        } catch (IOException e) {
            throw unreadable(e);
        }
    }

    private static int streamEntries(byte[] json, Consumer<ImportedWikiDocument> consumer) {
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            JsonToken first = parser.nextToken();
            if (first == JsonToken.START_ARRAY) {
                return readArray(parser, consumer);
            }
            if (first == JsonToken.START_OBJECT) {
                return readWrapped(parser, consumer);
            }
            throw unsupportedShape();
        } catch (IOException e) {
            throw unreadable(e);
        }
    }

    /**
     * Top level array: one element is read, emitted and released before the next is touched.
     */
    private static int readArray(JsonParser parser, Consumer<ImportedWikiDocument> consumer)
            throws IOException {
        int count = 0;
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "JSON 数组未正常结束");
            }
            if (token == JsonToken.END_ARRAY) {
                return count;
            }
            consumer.accept(toDocument(OBJECT_MAPPER.readTree(parser), count));
            count++;
        }
    }

    /**
     * Wrapper shape: fields other than {@link #WRAPPER_ARRAY_KEY} are skipped, so the wrapper object
     * is never built either.
     */
    private static int readWrapped(JsonParser parser, Consumer<ImportedWikiDocument> consumer)
            throws IOException {
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "JSON 未正常结束");
            }
            if (token == JsonToken.END_OBJECT) {
                throw unsupportedShape();
            }
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if (WRAPPER_ARRAY_KEY.equals(fieldName) && valueToken == JsonToken.START_ARRAY) {
                return readArray(parser, consumer);
            }
            parser.skipChildren();
        }
    }

    /**
     * Maps one entry onto the object the existing import pipeline consumes.
     */
    static ImportedWikiDocument toDocument(JsonNode entry, int entryIndex) {
        Map<String, Object> fields = entry != null && entry.isObject()
                ? OBJECT_MAPPER.convertValue(entry, FIELD_MAP)
                : new LinkedHashMap<>();
        String rawTitle = firstUsableValue(fields, TITLE_KEYS);
        String contentKey = firstUsableKey(fields, CONTENT_KEYS);
        String content = contentKey == null ? "" : text(fields.get(contentKey));
        String sourceUrl = text(fields.get(URL_KEY));

        ImportedWikiDocument document = new ImportedWikiDocument();
        String title = StrUtil.isBlank(rawTitle) ? fallbackTitle(sourceUrl, entryIndex) : rawTitle.trim();
        document.setTitle(truncate(title));
        document.setContent(content);
        document.setContentFormat(CONTENT_FORMAT);
        document.setSourceType(SOURCE_TYPE);
        document.setSourceUrl(StrUtil.isBlank(sourceUrl) ? null : sourceUrl);
        document.setMetadataJson(buildMetadata(fields, rawTitle, content, contentKey, entryIndex));
        return document;
    }

    /**
     * Serialises every original entry field as one JSON record, minus the field consumed as content.
     *
     * <p>The complete original title always rides along: the document column stores a truncated
     * title, so this record is the only place the full value survives. The detected language is
     * written here too — it is not an entry field, so nothing else would carry it, and the indexing
     * side must reach the same profile on a rebuild as it did on import or every chunk index would
     * shift.
     */
    private static String buildMetadata(Map<String, Object> fields, String rawTitle, String content,
                                       String contentKey, int entryIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>(fields);
        if (contentKey != null) {
            metadata.remove(contentKey);
        }
        metadata.put(ENTRY_INDEX_KEY, entryIndex);
        if (StrUtil.isNotBlank(rawTitle)) {
            metadata.put(TITLE_KEY, rawTitle);
        }
        metadata.put(LANGUAGE_KEY, ChunkerProfile.forContent(content).getName());
        return encodeWithinLimit(metadata);
    }

    /**
     * Serialises the record, dropping the least important keys until it fits the stored metadata
     * limit. The result is always valid JSON and the entry is imported either way.
     */
    private static String encodeWithinLimit(Map<String, Object> metadata) {
        String encoded = encode(metadata);
        if (encoded.length() <= MAX_METADATA_LENGTH) {
            return encoded;
        }
        for (String key : METADATA_DROP_ORDER) {
            if (metadata.remove(key) != null) {
                encoded = encode(metadata);
                if (encoded.length() <= MAX_METADATA_LENGTH) {
                    return encoded;
                }
            }
        }
        for (String key : new ArrayList<>(metadata.keySet())) {
            if (METADATA_KEEP_LAST.contains(key)) {
                continue;
            }
            metadata.remove(key);
            encoded = encode(metadata);
            if (encoded.length() <= MAX_METADATA_LENGTH) {
                return encoded;
            }
        }
        return TRUNCATED_METADATA;
    }

    private static String encode(Map<String, Object> metadata) {
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (IOException e) {
            return TRUNCATED_METADATA;
        }
    }

    /**
     * Fixed fallback chain, so a title-less entry still imports under a stable, non-empty title.
     */
    private static String fallbackTitle(String sourceUrl, int entryIndex) {
        String fromUrl = urlSegment(sourceUrl);
        if (StrUtil.isNotBlank(fromUrl)) {
            return fromUrl;
        }
        return "条目 " + (entryIndex + 1);
    }

    private static String urlSegment(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String path = uri.getPath() == null ? "" : uri.getPath();
            int slash = path.lastIndexOf('/');
            String last = (slash >= 0 ? path.substring(slash + 1) : path)
                    .replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
            if (!last.isBlank()) {
                return last;
            }
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstUsableValue(Map<String, Object> fields, List<String> keys) {
        for (String key : keys) {
            String value = text(fields.get(key));
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * First alias key holding usable text. The other aliases stay in the metadata record, so an entry
     * carrying both {@code content} and {@code body} remains traceable after the fact.
     */
    private static String firstUsableKey(Map<String, Object> fields, List<String> keys) {
        for (String key : keys) {
            if (StrUtil.isNotBlank(text(fields.get(key)))) {
                return key;
            }
        }
        return null;
    }

    /**
     * Only string values are usable as title or content; structured values are not coerced.
     */
    private static String text(Object value) {
        return value instanceof CharSequence ? value.toString() : null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_TITLE_LENGTH ? value : value.substring(0, MAX_TITLE_LENGTH);
    }

    private static BusinessException unsupportedShape() {
        return new BusinessException(ErrorCode.PARAMS_ERROR,
                "JSON 顶层既不是数组，也没有 " + WRAPPER_ARRAY_KEY + " 数组");
    }

    private static BusinessException unreadable(IOException e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new BusinessException(ErrorCode.PARAMS_ERROR,
                "JSON 文件解析失败：" + StrUtil.maxLength(detail, 200));
    }
}
