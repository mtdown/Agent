package com.et.cloud.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a downloaded (or uploaded) HTML page into readable Markdown.
 *
 * The four layers mirror the reference cleaner at
 * {@code F:/AIProject/my/03-html-cleaner/html_cleaner.py}:
 *
 * <ol>
 *   <li>L1 - remove non-content DOM: scripts, styles, hidden nodes, overlays and sharing controls.</li>
 *   <li>L2 - locate the article container: known site containers first, generic content hint second,
 *           the most paragraph-dense parent last.</li>
 *   <li>L3 - drop short UI-only lines, but only when they are not part of a recognized container.</li>
 *   <li>L4 - serialize the selected container with {@link HtmlToMarkdownConverter}.</li>
 * </ol>
 *
 * URL imports and local {@code .html} / {@code .htm} uploads share this class so both run the
 * identical cleaning policy.
 */
final class WebPageMarkdownCleaner {

    /**
     * L1: elements removed because they can never be part of the article body.
     */
    private static final List<String> DISCARD_SELECTORS = Arrays.asList(
            "script", "style", "noscript", "link", "meta", "iframe", "svg",
            "[hidden]", "[aria-hidden=true]",
            "[style*=\"display:none\"]", "[style*=\"display: none\"]",
            "[style*=\"visibility:hidden\"]", "[style*=\"visibility: hidden\"]",
            "[style*=\"opacity:0\"]", "[style*=\"opacity: 0\"]",
            ".weui-mask", ".weui-msg", ".weui-dialog", ".weui-msg__tips",
            ".mask", ".overlay", ".popup", ".modal", ".share", ".share-box",
            ".share-layer", ".js_share", "#js_share", ".js_share_mask",
            ".tips", ".toast", ".dialog", ".float-btn", ".back-top",
            ".rich_media_tool", ".rich_media_area_extra", ".qr_code",
            "#js_pc_qr_code", ".weui-msg__desc-area", ".profile_container",
            ".js_sns_media_area", ".rich_media_meta_list"
    );

    /**
     * L1 subset that only hides an element. A content-like container that is merely pre-hidden
     * (WeChat renders the article that way) must survive, otherwise the whole body is lost.
     */
    private static final Set<String> HIDE_RULES = new HashSet<>(Arrays.asList(
            "[hidden]", "[aria-hidden=true]",
            "[style*=\"display:none\"]", "[style*=\"display: none\"]",
            "[style*=\"visibility:hidden\"]", "[style*=\"visibility: hidden\"]",
            "[style*=\"opacity:0\"]", "[style*=\"opacity: 0\"]"
    ));

    private static final Pattern CONTENT_CLASS_HINT = Pattern.compile(
            "(js_content|rich_media_content|trs_editor|TRS_UEDITOR|article-content|main-content"
                    + "|post-content|entry-content|content|article)",
            Pattern.CASE_INSENSITIVE);

    private static final int PRE_HIDDEN_CONTENT_MIN_LENGTH = 200;

    /**
     * L2: known article containers, most specific first.
     */
    private static final List<String> CONTAINER_PRIORITY = Arrays.asList(
            "#js_content",
            ".rich_media_content",
            ".Post-RichText",
            ".RichText.ztext",
            ".trs_editor_view", ".TRS_UEDITOR",
            "#zoom", ".zoom",
            "article",
            ".article-content", ".article_content", ".article-detail",
            "#article-content"
    );

    private static final int KNOWN_CONTAINER_MIN_LENGTH = 40;

    private static final Pattern CONTENT_HINT = Pattern.compile(
            "(content|article|main|list|body|doc|text|editor)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CHROME_HINT = Pattern.compile(
            "(footer|header|nav|side|sidebar|menu|toolbar|comment|recommend|related|share|mask"
                    + "|dialog|popup|tips|crumb|bread|top|bottom|qrcode|qr_code|login|register)",
            Pattern.CASE_INSENSITIVE);

    private static final int GENERIC_CONTAINER_MIN_LENGTH = 120;

    private static final String GENERIC_CONTAINER_PREFIX = "[class提示]";

    /**
     * L3: UI-only phrases. A short line built entirely from these is page chrome, not content.
     */
    private static final List<String> UI_PHRASES = Arrays.asList(
            "知道了", "轻点两下取消", "取消在看",
            "微信扫一扫", "关注该公众号", "使用小程序", "使用完整服务", "可打开此内容", "允许", "取消",
            "赞", "在看", "分享", "留言", "收藏", "听过", "视频", "小程序", "轻点两下",
            "赞同了该文章", "发布于", "编辑于", "著作权归作者所有", "商业转载请联系作者获得授权",
            "未经授权", "禁止转载", "打开App", "点击查看", "关注者", "写文章", "相关推荐",
            "发私信", "还没有评论",
            "首页", "上一页", "下一页", "尾页", "转到第", "所在位置", "当前位置", "打印本页",
            "返回顶部", "分享到", "扫码关注", "长按识别二维码", "加入收藏", "设为主页", "更多",
            "政务公开", "政务服务", "互动交流", "走进重庆", "政策文件库", "政策解读"
    );

    private static final Pattern META_LINE = Pattern.compile(
            "^[\\s\\u3000]*(?:.{0,15})?(?:关注|赞同了该文章|发布于\\s?\\d|编辑于|著作权归作者所有"
                    + "|商业转载请联系作者获得授权|未经授权不得转载|禁止转载|本文首发于|微信公众号"
                    + "|阅读原文|点此复制|打开App|广告)[^。！？\\n]{0,40}?$");

    private static final String LINE_PUNCTUATION = "，。、；：:,.!?！？ ";

    private static final String RESIDUAL_PUNCTUATION = "，。、；：:,.!?！？ —|·・*[]()（）\"'“”";

    private static final int MAX_UI_LINE_LENGTH = 30;

    private static final int MAX_META_LINE_LENGTH = 60;

    private static final int MAX_DEDUP_LINE_LENGTH = 60;

    /** Longest phrase first, so "轻点两下取消" is removed before "轻点两下". */
    private static final List<String> UI_PHRASES_LONGEST_FIRST;

    static {
        List<String> sorted = new ArrayList<>(new LinkedHashSet<>(UI_PHRASES));
        sorted.sort((left, right) -> Integer.compare(right.length(), left.length()));
        UI_PHRASES_LONGEST_FIRST = Collections.unmodifiableList(sorted);
    }

    private WebPageMarkdownCleaner() {
    }

    /**
     * Reads the cleaned Markdown of {@code html}, or an empty string when nothing readable is found.
     */
    static String toMarkdown(String html) {
        return clean(html).getMarkdown();
    }

    /**
     * Runs the full four-layer pipeline and reports which container was used.
     */
    static CleanedPage clean(String html) {
        if (html == null || html.trim().isEmpty()) {
            return new CleanedPage("", "", false);
        }
        Document document = Jsoup.parse(html);
        dropNonContentElements(document);
        String knownSelector = pickKnownContainerSelector(document);
        Element container = knownSelector.isEmpty() ? null : document.selectFirst(knownSelector);
        String description = knownSelector;
        if (container == null) {
            container = pickGenericContainer(document);
            description = container == null ? "" : GENERIC_CONTAINER_PREFIX + identifierOf(container);
        }
        if (container == null) {
            container = pickParagraphParent(document);
            description = container == null ? "" : "<p> 父容器";
        }
        if (container == null) {
            // Last resort: the body itself. L1 already removed scripts, overlays and sharing
            // controls, and HtmlToMarkdownConverter additionally drops nav/footer/aside chrome, so
            // this still yields the best readable content area available on the page.
            container = document.body();
            description = container == null ? "" : "<body>";
        }
        if (container == null) {
            return new CleanedPage("", "", false);
        }
        String markdown = HtmlToMarkdownConverter.convert(container.html());
        markdown = filterNoiseLines(markdown);
        boolean generic = description.startsWith(GENERIC_CONTAINER_PREFIX);
        if (generic) {
            markdown = dedupShortLines(markdown);
        }
        return new CleanedPage(markdown, description, generic);
    }

    // ---------------------------------------------------------------- L1

    private static void dropNonContentElements(Document document) {
        for (String selector : DISCARD_SELECTORS) {
            for (Element element : document.select(selector)) {
                if (element.parent() == null) {
                    continue;
                }
                if (HIDE_RULES.contains(selector)
                        && isContentLike(element)
                        && element.text().length() > PRE_HIDDEN_CONTENT_MIN_LENGTH) {
                    continue;
                }
                element.remove();
            }
        }
    }

    private static boolean isContentLike(Element element) {
        return CONTENT_CLASS_HINT.matcher(identifierOf(element)).find();
    }

    private static String identifierOf(Element element) {
        String id = element.id() == null ? "" : element.id();
        String classNames = element.className() == null ? "" : element.className();
        String identifier = (id + " " + classNames).trim();
        return identifier.isEmpty() ? element.tagName() : identifier;
    }

    // ---------------------------------------------------------------- L2

    private static String pickKnownContainerSelector(Document document) {
        for (String selector : CONTAINER_PRIORITY) {
            Element candidate = document.selectFirst(selector);
            if (candidate != null && candidate.text().length() > KNOWN_CONTAINER_MIN_LENGTH) {
                return selector;
            }
        }
        return "";
    }

    private static Element pickGenericContainer(Document document) {
        Element best = null;
        int bestLength = 0;
        for (Element candidate : document.select("div, section, td, main")) {
            String identifier = identifierOf(candidate);
            if (!CONTENT_HINT.matcher(identifier).find()) {
                continue;
            }
            if (CHROME_HINT.matcher(identifier).find()) {
                continue;
            }
            int length = candidate.text().length();
            if (length > bestLength) {
                best = candidate;
                bestLength = length;
            }
        }
        return bestLength >= GENERIC_CONTAINER_MIN_LENGTH ? best : null;
    }

    private static Element pickParagraphParent(Document document) {
        Element body = document.body() != null ? document.body() : document;
        Elements paragraphs = body.select("p");
        if (paragraphs.isEmpty()) {
            return null;
        }
        Map<Element, Integer> counts = new IdentityHashMap<>();
        for (Element paragraph : paragraphs) {
            Element parent = paragraph.parent();
            if (parent != null) {
                counts.merge(parent, 1, Integer::sum);
            }
        }
        Element best = null;
        int bestCount = 0;
        for (Map.Entry<Element, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return bestCount >= 2 ? best : null;
    }

    // ---------------------------------------------------------------- L3

    private static String filterNoiseLines(String markdown) {
        List<String> kept = new ArrayList<>();
        for (String line : markdown.split("\n", -1)) {
            String stripped = stripChars(line.strip(), LINE_PUNCTUATION);
            if (stripped.isEmpty()) {
                kept.add(line);
                continue;
            }
            if (stripped.length() <= MAX_META_LINE_LENGTH && META_LINE.matcher(stripped).matches()) {
                continue;
            }
            String residual = stripped;
            for (String phrase : UI_PHRASES_LONGEST_FIRST) {
                residual = residual.replace(phrase, "");
            }
            residual = stripChars(residual.strip(), RESIDUAL_PUNCTUATION);
            if (stripped.length() <= MAX_UI_LINE_LENGTH && residual.isEmpty()) {
                continue;
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    private static String dedupShortLines(String markdown) {
        Set<String> seen = new HashSet<>();
        List<String> kept = new ArrayList<>();
        for (String line : markdown.split("\n", -1)) {
            String stripped = line.strip();
            if (!stripped.isEmpty() && stripped.length() <= MAX_DEDUP_LINE_LENGTH) {
                if (!seen.add(stripped)) {
                    continue;
                }
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    private static String stripChars(String value, String chars) {
        int start = 0;
        int end = value.length();
        while (start < end && chars.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && chars.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    /**
     * Outcome of {@link #clean(String)}.
     */
    static final class CleanedPage {

        private final String markdown;

        private final String container;

        private final boolean genericContainer;

        private CleanedPage(String markdown, String container, boolean genericContainer) {
            this.markdown = markdown;
            this.container = container;
            this.genericContainer = genericContainer;
        }

        String getMarkdown() {
            return markdown;
        }

        String getContainer() {
            return container;
        }

        boolean isGenericContainer() {
            return genericContainer;
        }

        @Override
        public String toString() {
            return "CleanedPage{container='" + container + "', generic=" + genericContainer
                    + ", length=" + markdown.length() + "}";
        }
    }
}
