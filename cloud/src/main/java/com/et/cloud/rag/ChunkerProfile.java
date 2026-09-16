package com.et.cloud.rag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chunking parameters, selected from the document's language.
 *
 * <p>The pipeline has a single chunking implementation; a profile is only a parameter set (character
 * cap, minimum chunk length, overlap and the characters that end a sentence). {@link #ZH} reproduces
 * the parameters that were hard-coded before this profile existed, so Chinese documents keep their
 * chunking byte for byte; {@link #EN} is for English corpora whose paragraphs are far longer than the
 * Chinese cap and whose sentences end on Latin punctuation.
 *
 * <p>Selection is a deterministic function of the content and never consults a model, so re-indexing
 * the same document always produces the same chunks.
 */
public final class ChunkerProfile {

    public static final String ZH_NAME = "zh";

    public static final String EN_NAME = "en";

    /**
     * Chinese: the historical values, unchanged. Overlap stays 80 and the sentence boundaries stay
     * {@code 。；} — deliberately including the pre-existing absence of {@code ！？}, because widening
     * them would change every already-indexed Chinese chunk.
     */
    public static final ChunkerProfile ZH = new ChunkerProfile(ZH_NAME, 600, 100, 80, "。；", false);

    /**
     * English: paragraphs in news corpora routinely run 800-1030 characters, so the Chinese cap
     * would force mechanical windows that cut inside words. 1800 characters keeps roughly the same
     * token mass per chunk as 600 Chinese characters, and a longer overlap suits Latin word length.
     */
    public static final ChunkerProfile EN = new ChunkerProfile(EN_NAME, 1800, 100, 100, ".!?;", true);

    /**
     * Share of language-bearing characters that must be CJK for a document to count as Chinese.
     * Ties and undecidable content fall back to {@link #ZH}, the pre-existing behaviour.
     */
    private static final double CJK_SHARE_THRESHOLD = 0.5d;

    private static final Pattern INITIALISM = Pattern.compile("[A-Z](\\.[A-Z])+");

    /**
     * Words whose trailing period is part of the word. Matching is case-insensitive and ignores the
     * dots inside an entry, so both {@code Nov.} and {@code e.g.} are recognised.
     */
    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "mt", "inc", "ltd", "llc", "co",
            "corp", "vs", "etc", "eg", "ie", "approx", "dept", "univ", "gov", "sen", "rep", "gen",
            "col", "capt", "lt", "sgt", "rev", "hon", "vol", "fig", "no", "pp", "est", "min", "max",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
            "mon", "tue", "tues", "wed", "thu", "thur", "thurs", "fri", "sat", "sun",
            "us", "uk", "eu", "un", "phd", "esq", "al"));

    private final String name;

    private final int maxChunkLength;

    private final int minChunkLength;

    private final int overlapLength;

    private final String sentenceBoundaries;

    /**
     * Whether an oversized run may also be cut at a space when no sentence boundary is in reach.
     * Chinese has no inter-word spaces, so its historical behaviour is sentence boundary or plain cut.
     */
    private final boolean wordBoundaryFallback;

    private ChunkerProfile(String name, int maxChunkLength, int minChunkLength, int overlapLength,
                           String sentenceBoundaries, boolean wordBoundaryFallback) {
        this.name = name;
        this.maxChunkLength = maxChunkLength;
        this.minChunkLength = minChunkLength;
        this.overlapLength = overlapLength;
        this.sentenceBoundaries = sentenceBoundaries;
        this.wordBoundaryFallback = wordBoundaryFallback;
    }

    public String getName() {
        return name;
    }

    public int getMaxChunkLength() {
        return maxChunkLength;
    }

    public int getMinChunkLength() {
        return minChunkLength;
    }

    public int getOverlapLength() {
        return overlapLength;
    }

    public String getSentenceBoundaries() {
        return sentenceBoundaries;
    }

    /**
     * Profile for a stored language name, or null when the name is unknown.
     */
    public static ChunkerProfile byName(String languageName) {
        if (languageName == null) {
            return null;
        }
        String normalised = languageName.trim().toLowerCase(Locale.ROOT);
        if (ZH_NAME.equals(normalised) || "zh-cn".equals(normalised) || "cn".equals(normalised)) {
            return ZH;
        }
        if (EN_NAME.equals(normalised) || "en-us".equals(normalised) || "en-gb".equals(normalised)) {
            return EN;
        }
        return null;
    }

    /**
     * Profile for a document, preferring the language recorded when it was imported.
     *
     * <p>The stored name wins so that indexing right after an import and re-indexing during a rebuild
     * cannot disagree: a rebuild re-reads the document row, and re-detecting a different language
     * there would renumber every chunk and invalidate already-bound evaluation anchors.
     */
    public static ChunkerProfile resolve(String content, String languageName) {
        ChunkerProfile named = byName(languageName);
        return named != null ? named : forContent(content);
    }

    /**
     * Pure language detection from the content: the share of CJK ideographs among the letters.
     * Punctuation, digits and whitespace are ignored so that formatting cannot swing the result.
     */
    public static ChunkerProfile forContent(String content) {
        if (content == null || content.isEmpty()) {
            return ZH;
        }
        long cjk = 0;
        long latin = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                latin++;
            }
        }
        long total = cjk + latin;
        if (total == 0) {
            return ZH;
        }
        return (double) cjk / total >= CJK_SHARE_THRESHOLD ? ZH : EN;
    }

    private static boolean isCjk(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    /**
     * Index of the last usable sentence boundary within {@code [start, end]}, or -1 when there is
     * none.
     */
    int lastSentenceEnd(CharSequence text, int start, int end) {
        for (int i = Math.min(end, text.length() - 1); i >= start; i--) {
            char c = text.charAt(i);
            if (sentenceBoundaries.indexOf(c) < 0) {
                continue;
            }
            if (wordBoundaryFallback && !isLatinSentenceEnd(text, i)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    /**
     * Index of the last inter-word space within {@code (start, end)}, or -1 when cutting at a space
     * is not allowed for this language.
     */
    int lastWordBoundary(CharSequence text, int start, int end) {
        if (!wordBoundaryFallback) {
            return -1;
        }
        for (int i = Math.min(end, text.length()) - 1; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A Latin period only ends a sentence when it is neither a decimal point nor part of an
     * abbreviation or an initialism. The other boundary characters are unambiguous.
     */
    private static boolean isLatinSentenceEnd(CharSequence text, int index) {
        char c = text.charAt(index);
        if (c != '.') {
            return true;
        }
        if (index > 0 && index + 1 < text.length()
                && Character.isDigit(text.charAt(index - 1)) && Character.isDigit(text.charAt(index + 1))) {
            return false;
        }
        String word = precedingWord(text, index);
        if (word.isEmpty()) {
            return true;
        }
        if (word.length() == 1 && Character.isUpperCase(word.charAt(0))) {
            return false;
        }
        if (INITIALISM.matcher(word).matches()) {
            return false;
        }
        return !ABBREVIATIONS.contains(word.replace(".", "").toLowerCase(Locale.ROOT));
    }

    /**
     * Letters and inner dots immediately before {@code index}, e.g. {@code Nov}, {@code U.S}.
     */
    private static String precedingWord(CharSequence text, int index) {
        int i = index - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (Character.isLetter(c) || c == '.') {
                i--;
                continue;
            }
            break;
        }
        return text.subSequence(i + 1, index).toString();
    }
}
