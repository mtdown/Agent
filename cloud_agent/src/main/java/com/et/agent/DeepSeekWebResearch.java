package com.et.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Request-scoped, bounded access to the DeepSeek web APIs. */
final class DeepSeekWebResearch {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BYTES = 262144;
    private final String key, base;
    private final int timeout;
    DeepSeekWebResearch(String key) { this(key, "https://api.deepseek.com", 10000); }
    DeepSeekWebResearch(String key, String base, int timeout) {
        this.key = key == null ? "" : key.trim(); this.base = base; this.timeout = timeout;
    }
    boolean enabled() { return !key.isEmpty(); }
    Map<String, AgentLoop.Tool> bind() {
        int[] attempts = {0, 0};
        Map<String, AgentLoop.Tool> result = new HashMap<>();
        result.put("web_search", raw -> execute(raw, true, attempts));
        result.put("web_fetch", raw -> execute(raw, false, attempts));
        return result;
    }
    private String execute(String raw, boolean search, int[] attempts) {
        try {
            JsonNode args = JSON.readTree(raw);
            String field = search ? "query" : "url";
            if (args == null || !args.isObject() || args.size() != 1 || !args.path(field).isTextual()) return error("invalid_argument");
            String value = args.path(field).asText();
            if (search ? value.trim().isEmpty() || value.codePointCount(0, value.length()) > 128 : !validUrl(value)) return error("invalid_argument");
            ObjectNode body = JSON.createObjectNode().put(field, value);
            if (search) { body.put("count", 5); body.put("need_content", false); }
            int index = search ? 0 : 1;
            for (int i = 0; i < 2; i++) {
                if (attempts[index] >= 3) return error("budget_exhausted");
                attempts[index]++;
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(base + (search ? "/v1/tools/search" : "/v1/tools/fetch")).openConnection();
                    conn.setConnectTimeout(timeout); conn.setReadTimeout(timeout); conn.setInstanceFollowRedirects(false);
                    conn.setRequestMethod("POST"); conn.setDoOutput(true);
                    conn.setRequestProperty("Authorization", "Bearer " + key);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    try (OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                    int status = conn.getResponseCode();
                    if (status == 200) {
                        try (InputStream in = conn.getInputStream()) { return success(JSON.readTree(readBounded(in)), search, value); }
                        finally { conn.disconnect(); }
                    }
                    conn.disconnect();
                    String category = status == 401 ? "authentication" : status == 402 ? "insufficient_balance" : status == 403 ? "forbidden" : status == 404 ? "not_found" : status == 429 ? "rate_limited" : status == 408 || status >= 500 ? "upstream_failure" : "request_error";
                    if ((status == 408 || status == 429 || status >= 500) && i == 0 && attempts[index] < 3) { pause(); continue; }
                    return error(category);
                } catch (java.net.SocketTimeoutException e) {
                    if (i == 0 && attempts[index] < 3) { pause(); continue; }
                    return error("timeout");
                } catch (IOException e) { return error("upstream_failure"); }
            }
        } catch (IOException e) { return error("invalid_argument"); }
        return error("upstream_failure");
    }
    private static boolean validUrl(String value) {
        if (value.length() > 8192) return false;
        try { URI uri = new URI(value); return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null && uri.getUserInfo() == null; }
        catch (URISyntaxException e) { return false; }
    }
    private static byte[] readBounded(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
        while ((n = in.read(buffer)) != -1) { if (out.size() + n > MAX_BYTES) throw new IOException("Response too large"); out.write(buffer, 0, n); }
        return out.toByteArray();
    }
    private String success(JsonNode root, boolean search, String url) throws IOException {
        if (root == null || !root.isObject()) throw new IOException("Invalid response");
        ObjectNode result = JSON.createObjectNode().put("status", "ok");
        if (search) {
            if (!root.path("results").isArray()) throw new IOException("Invalid response");
            ArrayNode values = result.putArray("results");
            for (JsonNode item : root.path("results")) {
                if (values.size() >= 5) break;
                ObjectNode entry = values.addObject();
                for (String f : new String[]{"title", "url", "snippet", "summary"}) entry.put(f, safe(item.path(f).asText(""), f.equals("summary") ? 800 : 500));
                entry.put("content_available", false);
            }
            if (values.isEmpty()) result.put("status", "no_results");
        } else {
            if (!root.path("content").isTextual()) throw new IOException("Invalid response");
            String content = safe(root.path("content").asText(), 5000);
            result.put("url", safe(url, 8192)); result.put("title", safe(root.path("title").asText(""), 500));
            result.put("content", content); result.put("content_available", !content.isEmpty());
        }
        return result.toString();
    }
    private String safe(String text, int max) { String clean = text.replace(key, "[redacted]"); return clean.substring(0, Math.min(max, clean.length())); }
    private static String error(String type) { return JSON.createObjectNode().put("status", "failed").put("error", type).toString(); }
    private static void pause() { try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AgentLoop.Cancelled(); } }
}
