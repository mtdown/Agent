package com.et.cloud.service;

import com.et.cloud.exception.BusinessException;
import com.et.cloud.model.dto.ImportedWikiDocument;
import com.et.cloud.service.impl.WikiDocumentImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WikiDocumentImportServiceTest {

    private final WikiDocumentImportService importService = new WikiDocumentImportServiceImpl();

    @Test
    void importMarkdownKeepsMarkdownFormatAndTitle() {
        MockMultipartFile file = file("产品方案.md", "text/markdown", "# 标题\n\n正文");

        ImportedWikiDocument result = importService.parse(file, null);

        assertEquals("产品方案", result.getTitle());
        assertEquals("# 标题\n\n正文", result.getContent());
        assertEquals("markdown", result.getContentFormat());
        assertEquals("UPLOAD", result.getSourceType());
        assertTrue(result.getMetadataJson().contains("\"sourceExtension\":\"md\""));
        assertTrue(result.getMetadataJson().contains("\"importedAs\":\"markdown\""));
    }

    @Test
    void importHtmlIsCleanedToStructuredMarkdown() {
        String rawHtml = "<!DOCTYPE html><html><head><title>页面标题</title><style>h1{color:red}</style></head>"
                + "<body><nav>导航菜单</nav><main>"
                + "<h1 onclick=\"evil()\">一级标题</h1>"
                + "<h2>二级标题</h2>"
                + "<h3>三级标题</h3>"
                + "<p>第一段<strong>加粗</strong>文本。</p>"
                + "<ul><li>列表甲</li><li>列表乙</li></ul>"
                + "<table><tr><th>列一</th><th>列二</th></tr><tr><td>甲</td><td>乙</td></tr></table>"
                + "<pre>code line</pre>"
                + "<script>alert(1)</script>"
                + "<img src=\"https://example.com/a.jpg\" alt=\"配图\">"
                + "</main><footer>页脚信息</footer></body></html>";
        MockMultipartFile file = file("page.html", "text/html", rawHtml);

        ImportedWikiDocument result = importService.parse(file, "Custom title");

        assertEquals("Custom title", result.getTitle());
        // HTML is stored as cleaned Markdown, never as raw markup.
        assertEquals("markdown", result.getContentFormat());
        String content = result.getContent();
        // Heading levels 1-3 are kept as structure marks.
        assertTrue(content.contains("# 一级标题"), content);
        assertTrue(content.contains("## 二级标题"), content);
        assertTrue(content.contains("### 三级标题"), content);
        // Plain text blocks are kept.
        assertTrue(content.contains("第一段加粗文本。"), content);
        assertTrue(content.contains("- 列表甲"), content);
        assertTrue(content.contains("| 列一 | 列二 |"), content);
        assertTrue(content.contains("```"), content);
        // Page chrome and resources are dropped.
        assertFalse(content.contains("导航菜单"), content);
        assertFalse(content.contains("页脚信息"), content);
        assertFalse(content.contains("alert"), content);
        assertFalse(content.contains("<script"), content);
        assertFalse(content.contains("<img"), content);
        assertFalse(content.contains("onclick"), content);
        assertTrue(result.getMetadataJson().contains("\"sourceExtension\":\"html\""));
        assertTrue(result.getMetadataJson().contains("\"importedAs\":\"markdown\""));
    }

    @Test
    void importHtmIsCleanedToMarkdownWithDefaultTitle() {
        MockMultipartFile file = file("归档页.htm", "text/html", "<h1>Archived</h1><p>归档正文</p>");

        ImportedWikiDocument result = importService.parse(file, null);

        assertEquals("归档页", result.getTitle());
        assertEquals("markdown", result.getContentFormat());
        assertTrue(result.getContent().contains("# Archived"), result.getContent());
        assertTrue(result.getContent().contains("归档正文"), result.getContent());
        assertTrue(result.getMetadataJson().contains("\"sourceExtension\":\"htm\""));
    }

    @Test
    void importRejectsHtmlWithoutExtractableText() {
        MockMultipartFile file = file("shell.html", "text/html",
                "<html><head><style>body{}</style><script>boot()</script></head><body></body></html>");

        BusinessException exception = assertThrows(BusinessException.class, () -> importService.parse(file, null));
        assertTrue(exception.getMessage().contains("未能从 HTML 中提取有效文本内容"));
    }

    @Test
    void importRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.md", "text/markdown", new byte[0]);

        assertThrows(BusinessException.class, () -> importService.parse(file, null));
    }

    @Test
    void importRejectsBlankContent() {
        MockMultipartFile file = file("blank.md", "text/markdown", "   \n\t ");

        assertThrows(BusinessException.class, () -> importService.parse(file, null));
    }

    @Test
    void importRejectsUnsupportedFile() {
        MockMultipartFile file = file("notes.txt", "text/plain", "plain text");

        assertThrows(BusinessException.class, () -> importService.parse(file, null));
    }

    @Test
    void importRejectsDocxFileBecauseWordIsOutOfScope() {
        MockMultipartFile file = file(
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not a valid docx"
        );

        assertThrows(BusinessException.class, () -> importService.parse(file, null));
    }

    private MockMultipartFile file(String filename, String contentType, String content) {
        return new MockMultipartFile("file", filename, contentType, content.getBytes(StandardCharsets.UTF_8));
    }
}
