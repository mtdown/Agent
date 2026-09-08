package com.et.cloud.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour of the four-layer HTML cleaner shared by URL imports and local HTML uploads.
 */
class WebPageMarkdownCleanerTest {

    @Test
    void removesScriptsHiddenNodesOverlaysAndSharingControls() {
        String html = "<html><body>"
                + "<div id=\"js_content\">"
                + "<p>这是正文第一段，长度足够长以便通过容器选择阈值的要求。</p>"
                + "<p>这是正文第二段，同样需要有足够的长度来避免被过滤掉。</p>"
                + "<p>第三段正文内容，用于确认清洗后仍然保留完整文章结构。</p>"
                + "</div>"
                + "<script>alert('x')</script>"
                + "<style>.a{color:red}</style>"
                + "<div class=\"overlay\">浮层文案</div>"
                + "<div class=\"share\">分享到朋友圈</div>"
                + "<div style=\"display:none\">隐藏内容</div>"
                + "</body></html>";

        String markdown = WebPageMarkdownCleaner.toMarkdown(html);

        assertTrue(markdown.contains("这是正文第一段"), markdown);
        assertTrue(markdown.contains("第三段正文内容"), markdown);
        assertFalse(markdown.contains("alert"), markdown);
        assertFalse(markdown.contains("浮层文案"), markdown);
        assertFalse(markdown.contains("分享到朋友圈"), markdown);
        assertFalse(markdown.contains("隐藏内容"), markdown);
    }

    @Test
    void keepsPreHiddenArticleContainerWhenItCarriesTheBody() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            body.append("<p>正文段落").append(i).append("，这里是足够长的正文内容用于超过预隐藏保护阈值。</p>");
        }
        String html = "<html><body><div id=\"js_content\" style=\"visibility:hidden\">"
                + body
                + "</div><div class=\"qr_code\">二维码</div></body></html>";

        String markdown = WebPageMarkdownCleaner.toMarkdown(html);

        assertTrue(markdown.contains("正文段落0"), markdown);
        assertTrue(markdown.contains("正文段落9"), markdown);
        assertFalse(markdown.contains("二维码"), markdown);
    }

    @Test
    void prefersKnownArticleContainerOverGenericContentHint() {
        String generic = "通用的列表区域内容".repeat(20);
        String article = "这是文章容器内的正文内容，用于确认已知容器优先于泛用兜底区域被选中，"
                + "并且长度需要超过已知容器的最小长度阈值才能命中。";
        String html = "<html><body>"
                + "<div class=\"main-content\"><p>" + generic + "</p></div>"
                + "<article><p>" + article + "</p></article>"
                + "</body></html>";

        WebPageMarkdownCleaner.CleanedPage cleaned = WebPageMarkdownCleaner.clean(html);

        assertEquals("article", cleaned.getContainer());
        assertFalse(cleaned.isGenericContainer());
        assertTrue(cleaned.getMarkdown().contains("文章容器内的正文内容"), cleaned.getMarkdown());
        assertFalse(cleaned.getMarkdown().contains("通用的列表区域内容"), cleaned.getMarkdown());
    }

    @Test
    void fallsBackToGenericContentAreaWhenNoKnownContainerExists() {
        String readable = "泛用正文区域里的一段可读内容，需要足够长才能满足泛用兜底的最小长度要求。".repeat(4);
        String html = "<html><body>"
                + "<div class=\"content-body\"><p>" + readable + "</p></div>"
                + "<div class=\"footer\">页脚版权信息</div>"
                + "</body></html>";

        WebPageMarkdownCleaner.CleanedPage cleaned = WebPageMarkdownCleaner.clean(html);

        assertTrue(cleaned.getContainer().startsWith("[class提示]"), cleaned.getContainer());
        assertTrue(cleaned.isGenericContainer());
        assertTrue(cleaned.getMarkdown().contains("泛用正文区域里的一段可读内容"), cleaned.getMarkdown());
        assertFalse(cleaned.getMarkdown().contains("页脚版权信息"), cleaned.getMarkdown());
    }

    @Test
    void dropsShortUiOnlyLines() {
        String readable = "真正的正文内容需要足够长，才能避免被当作界面文案过滤掉，这里重复几次凑够长度。".repeat(2);
        String html = "<html><body><div class=\"content-body\">"
                + "<p>" + readable + "</p>"
                + "<p>微信扫一扫</p>"
                + "<p>关注该公众号</p>"
                + "<p>赞</p>"
                + "<p>在看</p>"
                + "<p>分享到</p>"
                + "</div></body></html>";

        String markdown = WebPageMarkdownCleaner.toMarkdown(html);

        assertTrue(markdown.contains("真正的正文内容需要足够长"), markdown);
        assertFalse(markdown.contains("微信扫一扫"), markdown);
        assertFalse(markdown.contains("关注该公众号"), markdown);
        assertFalse(markdown.contains("分享到"), markdown);
    }

    @Test
    void keepsRepeatedShortLinesInsideKnownArticleContainer() {
        String html = "<html><body><article>"
                + "<p>文章容器的署名行可以合法重复出现，不应该被去重逻辑删除掉。</p>"
                + "<p>联系方式：023-12345678</p>"
                + "<p>联系方式：023-12345678</p>"
                + "</article></body></html>";

        String markdown = WebPageMarkdownCleaner.toMarkdown(html);

        assertEquals(2, countOccurrences(markdown, "联系方式：023-12345678"), markdown);
    }

    @Test
    void deduplicatesRepeatedShortLinesOnlyForGenericContainer() {
        String filler = "泛用区域的可读正文，需要满足最小长度阈值才能被选中作为兜底容器使用。".repeat(4);
        String html = "<html><body><div class=\"content-body\">"
                + "<p>" + filler + "</p>"
                + "<p>重复导航项标题</p>"
                + "<p>重复导航项标题</p>"
                + "</div></body></html>";

        String markdown = WebPageMarkdownCleaner.toMarkdown(html);

        assertEquals(1, countOccurrences(markdown, "重复导航项标题"), markdown);
    }

    @Test
    void returnsEmptyMarkdownForShellPageWithoutReadableContent() {
        String html = "<html><head><script>boot()</script><style>body{}</style></head>"
                + "<body></body></html>";

        WebPageMarkdownCleaner.CleanedPage cleaned = WebPageMarkdownCleaner.clean(html);

        assertTrue(cleaned.getMarkdown().isBlank(), cleaned.getMarkdown());
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }
}
