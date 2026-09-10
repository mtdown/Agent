package com.et.cloud.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits Markdown into chunks aligned to the heading hierarchy, with length guardrails:
 * oversized sections are split by paragraph, tiny sections merged into neighbours,
 * adjacent chunks share a small overlap so clause context is not cut off.
 *
 * Pure static utility, no Spring / DB coupling.
 */
public final class MarkdownChunker {

    public static final int MAX_CHUNK_LENGTH = 600;

    public static final int MIN_CHUNK_LENGTH = 100;

    public static final int OVERLAP_LENGTH = 80;

    /**
     * Government document number, e.g. 渝府办发〔2026〕24号.
     */
    public static final Pattern DOC_NUMBER_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5]{2,12}〔\\d{4}〕\\d+号");

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*$");

    private MarkdownChunker() {
    }

    /**
     * Extracts the first document number found in the text, or null.
     */
    public static String extractDocNumber(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        Matcher matcher = DOC_NUMBER_PATTERN.matcher(content);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * Chunks a Markdown document.
     */
    public static List<MarkdownChunk> chunk(String content) {
        List<MarkdownChunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        // 1. Split into heading-aligned sections.
        List<Section> sections = splitSections(content);
        // 2. Split oversized sections by paragraph, keep heading path.
        List<Section> pieces = new ArrayList<>();
        for (Section section : sections) {
            if (section.text.length() <= MAX_CHUNK_LENGTH) {
                pieces.add(section);
                continue;
            }
            pieces.addAll(splitOversized(section));
        }
        // 3. Merge tiny sections forward (accumulate until >= MIN or end).
        List<Section> merged = mergeTiny(pieces);
        // 4. Emit chunks with overlap from previous chunk tail.
        String prevTail = null;
        int index = 0;
        for (Section section : merged) {
            String text = section.text.strip();
            if (text.isEmpty()) {
                continue;
            }
            if (prevTail != null && !prevTail.isEmpty()) {
                text = prevTail + "\n" + text;
            }
            chunks.add(new MarkdownChunk(index++, section.headingPath, text));
            prevTail = tailOf(text, OVERLAP_LENGTH);
        }
        return chunks;
    }

    private static List<Section> splitSections(String content) {
        List<Section> sections = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        StringBuilder current = new StringBuilder();
        List<String> headingStack = new ArrayList<>();
        boolean inFrontMatter = false;
        if (lines.length > 0 && lines[0].strip().equals("---")) {
            inFrontMatter = true;
        }
        for (String line : lines) {
            if (inFrontMatter) {
                current.append(line).append('\n');
                if (line.strip().equals("---") || line.strip().equals("...")) {
                    inFrontMatter = false;
                }
                continue;
            }
            Matcher heading = HEADING_PATTERN.matcher(line);
            if (heading.matches()) {
                // heading line closes the current section
                if (current.length() > 0) {
                    sections.add(new Section(joinPath(headingStack), current.toString()));
                }
                int level = heading.group(1).length();
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(heading.group(2));
                // the heading line itself leads the new section text (helps embedding)
                current = new StringBuilder(line).append('\n');
                continue;
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) {
            sections.add(new Section(joinPath(headingStack), current.toString()));
        }
        // drop sections that carry only a heading line (no body at all)
        sections.removeIf(s -> s.text.strip().matches("^#{1,6}\\s+[^\\n]*"));
        return sections;
    }

    private static List<Section> splitOversized(Section section) {
        List<Section> parts = new ArrayList<>();
        String[] paragraphs = section.text.split("\n\\s*\n");
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (buffer.length() + p.length() + 2 > MAX_CHUNK_LENGTH && buffer.length() > 0) {
                parts.add(new Section(section.headingPath, buffer.toString()));
                buffer = new StringBuilder();
            }
            if (p.length() > MAX_CHUNK_LENGTH) {
                // single paragraph longer than the cap: hard-split by sentences-ish segments
                for (String seg : hardSplit(p, MAX_CHUNK_LENGTH)) {
                    parts.add(new Section(section.headingPath, seg));
                }
                continue;
            }
            buffer.append(p).append("\n\n");
        }
        if (buffer.length() > 0) {
            parts.add(new Section(section.headingPath, buffer.toString()));
        }
        return parts;
    }

    private static List<String> hardSplit(String text, int cap) {
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + cap, text.length());
            if (end < text.length()) {
                int dot = text.lastIndexOf('。', end);
                int semicolon = text.lastIndexOf('；', end);
                int cut = Math.max(dot, semicolon);
                if (cut > start + cap / 2) {
                    end = cut + 1;
                }
            }
            out.add(text.substring(start, end));
            start = end;
        }
        return out;
    }

    private static List<Section> mergeTiny(List<Section> pieces) {
        List<Section> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String headingPath = null;
        for (Section piece : pieces) {
            if (headingPath == null) {
                headingPath = piece.headingPath;
            }
            buffer.append(piece.text.strip()).append("\n\n");
            if (buffer.length() >= MIN_CHUNK_LENGTH) {
                merged.add(new Section(headingPath, buffer.toString()));
                buffer = new StringBuilder();
                headingPath = null;
            }
        }
        if (buffer.length() > 0) {
            if (merged.isEmpty()) {
                merged.add(new Section(headingPath == null ? "" : headingPath, buffer.toString()));
            } else {
                Section last = merged.get(merged.size() - 1);
                if (last.text.length() + buffer.length() <= MAX_CHUNK_LENGTH) {
                    last.text = last.text + buffer;
                } else {
                    // merging would overflow the cap: emit the leftover as its own section
                    merged.add(new Section(last.headingPath, buffer.toString()));
                }
            }
        }
        return merged;
    }

    private static String tailOf(String text, int length) {
        if (text.length() <= length) {
            return text;
        }
        String tail = text.substring(text.length() - length);
        int lineBreak = tail.indexOf('\n');
        if (lineBreak >= 0 && lineBreak < length / 2) {
            tail = tail.substring(lineBreak + 1);
        }
        return tail;
    }

    private static String joinPath(List<String> headingStack) {
        return String.join(" > ", headingStack);
    }

    private static final class Section {
        private String headingPath;
        private String text;

        private Section(String headingPath, String text) {
            this.headingPath = headingPath == null ? "" : headingPath;
            this.text = text;
        }
    }
}
