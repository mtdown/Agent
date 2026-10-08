package com.et.cloud.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts optional provenance fields from an article page without coupling them to its body. */
final class ArticleMetadataExtractor {

    private static final Pattern FRONT_MATTER_FIELD = Pattern.compile(
            "(?:^|\\s)([A-Za-z][A-Za-z0-9_-]*)\\s*:\\s*(.*?)(?=\\s+[A-Za-z][A-Za-z0-9_-]*\\s*:|$)");

    private ArticleMetadataExtractor() {
    }

    static Map<String, String> extract(String html, String sourceUrl) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (html != null && !html.isBlank()) {
            Document document = Jsoup.parse(html);
            putIfPresent(metadata, "articleTitle", firstNonBlank(
                    document.select("meta[property=og:title]").attr("content"),
                    document.select("meta[name=twitter:title]").attr("content"),
                    document.select("meta[itemprop=headline]").attr("content")));
            String canonicalUrl = firstNonBlank(
                    document.select("link[rel=canonical]").attr("href"),
                    document.select("meta[property=og:url]").attr("content"),
                    sourceUrl);
            putIfPresent(metadata, "sourceUrl", safeHttpUrl(canonicalUrl));
            for (Element tag : document.select("meta[property], meta[name], meta[itemprop]")) {
                String key = firstNonBlank(tag.attr("property"), tag.attr("name"), tag.attr("itemprop"));
                String value = firstNonBlank(tag.attr("content"), tag.attr("value"));
                addMetaValue(metadata, key, value);
            }
            for (Element script : document.select("script[type=application/ld+json]")) {
                try {
                    collectJsonLd(JSONUtil.parseObj(script.data()), metadata);
                } catch (Exception ignored) {
                    // A malformed JSON-LD block must not prevent importing readable article text.
                }
            }
        }
        if (!metadata.containsKey("sourceUrl")) {
            putIfPresent(metadata, "sourceUrl", safeHttpUrl(sourceUrl));
        }
        if (!metadata.containsKey("sourceSite")) {
            String host = hostOf(sourceUrl);
            if (host != null) {
                metadata.put("sourceSite", host);
            }
        }
        return metadata;
    }

    static ParsedMarkdown parseMarkdownFrontMatter(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return new ParsedMarkdown(markdown, new LinkedHashMap<>());
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        if (lines.length > 0 && lines[0].startsWith("\uFEFF")) {
            lines[0] = lines[0].substring(1);
        }
        Map<String, String> rawFields = new LinkedHashMap<>();
        int bodyLine;
        if (lines.length > 0 && "---".equals(lines[0].trim())) {
            int closingLine = -1;
            for (int i = 1; i < lines.length; i++) {
                if ("---".equals(lines[i].trim()) || "...".equals(lines[i].trim())) {
                    closingLine = i;
                    break;
                }
                parseFrontMatterLine(lines[i], rawFields);
            }
            if (closingLine < 0) {
                return new ParsedMarkdown(markdown, new LinkedHashMap<>());
            }
            bodyLine = closingLine + 1;
        } else {
            int line = 0;
            int parsedValues = 0;
            while (line < lines.length) {
                String candidate = lines[line].trim();
                if (candidate.isEmpty()) {
                    line++;
                    continue;
                }
                if (line == 0 && candidate.startsWith("--- ")) {
                    candidate = candidate.substring(4).trim();
                }
                int added = parseFrontMatterLine(candidate, rawFields);
                if (added == 0) {
                    break;
                }
                parsedValues += added;
                line++;
            }
            if (parsedValues < 2 || rawFields.keySet().stream().noneMatch(ArticleMetadataExtractor::isArticleMetadataKey)) {
                return new ParsedMarkdown(markdown, new LinkedHashMap<>());
            }
            bodyLine = line;
        }

        Map<String, String> metadata = normalizeFrontMatter(rawFields);
        StringBuilder body = new StringBuilder();
        for (int i = bodyLine; i < lines.length; i++) {
            body.append(lines[i]);
            if (i < lines.length - 1) {
                body.append('\n');
            }
        }
        String cleanBody = body.toString().replaceFirst("^(?:\\s*\\n)+", "");
        String sourceUrl = metadata.get("sourceUrl");
        if (!metadata.containsKey("sourceSite")) {
            putIfPresent(metadata, "sourceSite", hostOf(sourceUrl));
        }
        return new ParsedMarkdown(cleanBody, metadata);
    }

    private static void addMetaValue(Map<String, String> metadata, String key, String value) {
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.isEmpty()) {
            return;
        }
        if (isPublicationDateKey(normalizedKey)) {
            metadata.putIfAbsent("publishedAt", normalizedValue);
        } else if (isSourceSiteKey(normalizedKey)) {
            metadata.putIfAbsent("sourceSite", normalizedValue);
        } else if (isAuthorKey(normalizedKey)) {
            metadata.putIfAbsent("originalAuthor", normalizedValue);
        } else if (normalizedKey.equals("og:title") || normalizedKey.equals("headline")) {
            metadata.putIfAbsent("articleTitle", normalizedValue);
        } else if (normalizedKey.equals("og:url") || normalizedKey.equals("canonical")) {
            metadata.putIfAbsent("sourceUrl", safeHttpUrl(normalizedValue));
        }
    }

    private static boolean isPublicationDateKey(String key) {
        return key.equals("article:published_time") || key.equals("datepublished")
                || key.equals("pubdate") || key.equals("publishdate") || key.equals("publish_date")
                || key.equals("date") || key.equals("dc.date.issued") || key.equals("dcterms.created");
    }

    private static boolean isSourceSiteKey(String key) {
        return key.equals("og:site_name") || key.equals("application-name") || key.equals("publisher");
    }

    private static boolean isAuthorKey(String key) {
        return key.equals("author") || key.equals("article:author") || key.equals("parsely-author")
                || key.equals("byl");
    }

    private static void collectJsonLd(JSONObject object, Map<String, String> metadata) {
        if (object == null) {
            return;
        }
        putIfAbsent(metadata, "publishedAt", firstNonBlank(object.getStr("datePublished"), object.getStr("dateCreated")));
        putIfAbsent(metadata, "originalAuthor", nameOf(object.get("author")));
        putIfAbsent(metadata, "sourceSite", nameOf(object.get("publisher")));
        Object graph = object.get("@graph");
        if (graph instanceof JSONArray) {
            for (Object entry : (JSONArray) graph) {
                collectJsonLdNode(entry, metadata);
            }
        }
    }

    private static void collectJsonLdNode(Object node, Map<String, String> metadata) {
        if (node instanceof JSONObject) {
            collectJsonLd((JSONObject) node, metadata);
        } else if (node instanceof JSONArray) {
            for (Object entry : (JSONArray) node) {
                collectJsonLdNode(entry, metadata);
            }
        }
    }

    private static String nameOf(Object value) {
        if (value instanceof String) {
            return ((String) value).trim();
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String name = firstNonBlank(object.getStr("name"), object.getStr("alternateName"));
            return name;
        }
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            for (Object entry : values) {
                String name = nameOf(entry);
                if (name != null) {
                    return name;
                }
            }
        }
        return null;
    }

    private static void putIfAbsent(Map<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.putIfAbsent(key, value.trim());
        }
    }

    private static String hostOf(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String safeHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    ? uri.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseFrontMatterLine(String line, Map<String, String> fields) {
        Matcher matcher = FRONT_MATTER_FIELD.matcher(line.trim());
        int parsed = 0;
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = unquote(matcher.group(2).trim());
            if (!value.isEmpty()) {
                fields.putIfAbsent(key, value);
                parsed++;
            }
        }
        return parsed;
    }

    private static boolean isArticleMetadataKey(String key) {
        return key.equals("title") || key.equals("pubdate") || key.equals("publishedat")
                || key.equals("datepublished") || key.equals("sourceurl") || key.equals("url")
                || key.equals("sourcesite") || key.equals("sitename") || key.equals("author")
                || key.equals("originalauthor");
    }

    private static Map<String, String> normalizeFrontMatter(Map<String, String> fields) {
        Map<String, String> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "articleTitle", firstValue(fields, "title"));
        putIfPresent(metadata, "publishedAt", firstValue(fields, "publishedat", "pubdate", "datepublished", "date"));
        putIfPresent(metadata, "sourceSite", firstValue(fields, "sourcesite", "sitename", "site_name"));
        putIfPresent(metadata, "sourceUrl", safeHttpUrl(firstValue(fields, "sourceurl", "url", "canonicalurl")));
        putIfPresent(metadata, "originalAuthor", firstValue(fields, "originalauthor", "author"));
        return metadata;
    }

    private static String firstValue(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }

    private static void putIfPresent(Map<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.putIfAbsent(key, value.trim());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    static final class ParsedMarkdown {
        private final String content;
        private final Map<String, String> metadata;

        private ParsedMarkdown(String content, Map<String, String> metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        String getContent() {
            return content;
        }

        Map<String, String> getMetadata() {
            return metadata;
        }
    }
}
