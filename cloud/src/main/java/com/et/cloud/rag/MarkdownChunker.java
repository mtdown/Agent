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
 * <p>Every length and boundary decision comes from the supplied {@link ChunkerProfile}, so the
 * pipeline stays a single implementation while Chinese and English documents each get parameters
 * that fit their prose.
 *
 * Pure static utility, no Spring / DB coupling.
 */
public final class MarkdownChunker {

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
     * Chunks a Markdown document using the parameters of the given profile.
     */
    public static List<MarkdownChunk> chunk(String content, ChunkerProfile profile) {
        List<MarkdownChunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        // 1. Split into heading-aligned sections.
        List<Section> sections = splitSections(content);
        // 2. Split oversized sections by paragraph, keep heading path.
        List<Section> pieces = new ArrayList<>();
        for (Section section : sections) {
            if (section.text.length() <= profile.getMaxChunkLength()) {
                pieces.add(section);
                continue;
            }
            pieces.addAll(splitOversized(section, profile));
        }
        // 3. Merge tiny sections forward (accumulate until >= MIN or end).
        List<Section> merged = mergeTiny(pieces, profile);
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
            prevTail = tailOf(text, profile.getOverlapLength());
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

    private static List<Section> splitOversized(Section section, ChunkerProfile profile) {
        List<Section> parts = new ArrayList<>();
        int cap = profile.getMaxChunkLength();
        String[] paragraphs = section.text.split("\n\\s*\n");
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (buffer.length() + p.length() + 2 > cap && buffer.length() > 0) {
                parts.add(new Section(section.headingPath, buffer.toString()));
                buffer = new StringBuilder();
            }
            if (p.length() > cap) {
                // single paragraph longer than the cap: hard-split by sentences-ish segments
                for (String seg : hardSplit(p, profile)) {
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

    /**
     * Cuts an over-long run into windows of at most {@code profile.maxChunkLength}.
     *
     * <p>A window is closed at a sentence boundary when one is available past the half-way point,
     * otherwise (English only) at a word boundary, and otherwise at the raw cap so that no word,
     * decimal number or abbreviation is ever split in two.
     */
    private static List<String> hardSplit(String text, ChunkerProfile profile) {
        int cap = profile.getMaxChunkLength();
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + cap, text.length());
            if (end < text.length()) {
                int sentence = profile.lastSentenceEnd(text, start, end);
                if (sentence > start + cap / 2) {
                    end = sentence + 1;
                } else {
                    int word = profile.lastWordBoundary(text, start, end);
                    if (word > start + cap / 2) {
                        end = word + 1;
                    }
                }
            }
            out.add(text.substring(start, end));
            start = end;
        }
        return out;
    }

    private static List<Section> mergeTiny(List<Section> pieces, ChunkerProfile profile) {
        List<Section> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String headingPath = null;
        for (Section piece : pieces) {
            if (headingPath == null) {
                headingPath = piece.headingPath;
            }
            buffer.append(piece.text.strip()).append("\n\n");
            if (buffer.length() >= profile.getMinChunkLength()) {
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
                if (last.text.length() + buffer.length() <= profile.getMaxChunkLength()) {
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
