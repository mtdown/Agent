package com.et.cloud.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a downloaded HTML page into structured Markdown text.
 *
 * The wiki no longer tries to render uploaded HTML faithfully: downloaded pages reference
 * sibling CSS/JS/image resources that a single-file import cannot bring along, so the raw
 * markup is cleaned instead. Headings h1-h6 (levels 1-3 are the primary structure marks) and
 * plain text blocks (paragraphs, lists, tables, code) are kept; scripts, styles, navigation,
 * images and other page chrome are dropped.
 */
final class HtmlToMarkdownConverter {

    private static final String NOISE_SELECTOR = String.join(", ",
            "script", "style", "noscript", "nav", "footer", "aside", "form", "iframe",
            "svg", "canvas", "button", "select", "textarea", "img", "picture", "video", "audio");

    private HtmlToMarkdownConverter() {
    }

    static String convert(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        document.select(NOISE_SELECTOR).remove();
        Element root = document.body() != null ? document.body() : document;
        StringBuilder markdown = new StringBuilder();
        appendBlocks(root, markdown);
        return markdown.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static void appendBlocks(Element parent, StringBuilder out) {
        for (Node node : parent.childNodes()) {
            if (node instanceof TextNode) {
                String text = ((TextNode) node).text().trim();
                if (!text.isEmpty()) {
                    out.append(text).append("\n\n");
                }
                continue;
            }
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;
            String tag = element.tagName();
            switch (tag) {
                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                case "h6": {
                    int level = tag.charAt(1) - '0';
                    String text = element.text().trim();
                    if (!text.isEmpty()) {
                        out.append("#".repeat(level)).append(' ').append(text).append("\n\n");
                    }
                    break;
                }
                case "p": {
                    String text = element.text().trim();
                    if (!text.isEmpty()) {
                        out.append(text).append("\n\n");
                    }
                    break;
                }
                case "ul":
                    appendList(element, out, false, 0);
                    break;
                case "ol":
                    appendList(element, out, true, 0);
                    break;
                case "pre": {
                    String code = element.text().strip();
                    if (!code.isEmpty()) {
                        out.append("```\n").append(code).append("\n```\n\n");
                    }
                    break;
                }
                case "blockquote": {
                    String text = element.text().trim();
                    if (!text.isEmpty()) {
                        out.append("> ").append(text.replace("\n", "\n> ")).append("\n\n");
                    }
                    break;
                }
                case "table":
                    appendTable(element, out);
                    break;
                case "hr":
                    out.append("\n---\n\n");
                    break;
                case "br":
                    out.append('\n');
                    break;
                default:
                    // Containers (div/section/article/main/...) recurse so headings and blocks
                    // nested inside layout wrappers are still found.
                    appendBlocks(element, out);
                    break;
            }
        }
    }

    private static void appendList(Element list, StringBuilder out, boolean ordered, int depth) {
        int index = 1;
        for (Element item : list.children()) {
            if (!"li".equals(item.tagName())) {
                continue;
            }
            StringBuilder itemText = new StringBuilder();
            for (Node child : item.childNodes()) {
                if (child instanceof Element) {
                    String childTag = ((Element) child).tagName();
                    if ("ul".equals(childTag) || "ol".equals(childTag)) {
                        continue;
                    }
                    itemText.append(((Element) child).text());
                } else if (child instanceof TextNode) {
                    itemText.append(((TextNode) child).text());
                }
            }
            String text = itemText.toString().trim();
            if (!text.isEmpty()) {
                String marker = ordered ? (index + ". ") : "- ";
                out.append("  ".repeat(depth)).append(marker).append(text).append('\n');
            }
            index++;
            for (Element nested : item.children()) {
                if ("ul".equals(nested.tagName())) {
                    appendList(nested, out, false, depth + 1);
                } else if ("ol".equals(nested.tagName())) {
                    appendList(nested, out, true, depth + 1);
                }
            }
        }
        out.append('\n');
    }

    private static void appendTable(Element table, StringBuilder out) {
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element cell : tr.select("th, td")) {
                cells.add(cell.text().trim().replace("|", "\\|"));
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        for (List<String> row : rows) {
            while (row.size() < columns) {
                row.add("");
            }
        }
        out.append("| ").append(String.join(" | ", rows.get(0))).append(" |\n");
        out.append("|").append(" --- |".repeat(columns)).append('\n');
        for (int i = 1; i < rows.size(); i++) {
            out.append("| ").append(String.join(" | ", rows.get(i))).append(" |\n");
        }
        out.append('\n');
    }
}
