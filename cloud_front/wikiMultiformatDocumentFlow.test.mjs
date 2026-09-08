import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [apiSource, pageSource, treeSource, listSource, editorSource, viewerSource, detailSource] =
  await Promise.all([
    readSource('./src/api/documentWikiController.ts'),
    readSource('./src/pages/documentWiki/DocumentWikiListPage.vue'),
    readSource('./src/pages/documentWiki/components/WikiSpaceTree.vue'),
    readSource('./src/pages/documentWiki/components/WikiDocumentList.vue'),
    readSource('./src/components/DocumentWikiEditor.vue'),
    readSource('./src/components/DocumentWikiContentViewer.vue'),
    readSource('./src/pages/documentWiki/DocumentWikiDetailPage.vue'),
  ])

test('document wiki API exposes local document import endpoint', () => {
  assert.match(apiSource, /importDocumentWikiUsingPost/)
  assert.match(apiSource, /\/api\/documentWiki\/import/)
  assert.match(apiSource, /formData\.append\('file', file\)/)
})

test('space tree exposes upload beside document creation and emits selected target', () => {
  assert.match(treeSource, />\s*上传\s*<\/a-button>/)
  assert.match(treeSource, /accept="\.md,\.html,\.htm"/)
  assert.match(treeSource, /uploadDocument:\s*\[selection:\s*WikiTreeSelection,\s*file:\s*File\]/)
  assert.match(treeSource, /emit\('uploadDocument',\s*currentSelectionPayload\(\),\s*file\)/)
})

test('wiki page imports files into current selection and opens imported document inline', () => {
  assert.match(pageSource, /@upload-document="uploadDocument"/)
  assert.match(pageSource, /importDocumentWikiUsingPost/)
  assert.match(pageSource, /folderId:\s*selection\.folderId\s*\?\?\s*undefined/)
  assert.match(pageSource, /await\s+refreshWorkspaceAfterSave\(res\.data\.data,\s*selection\.spaceId\)/)
  assert.doesNotMatch(pageSource, /router\.push\(`\/documentWiki\/\$\{res\.data\.data\}`\)/)
})

test('upload flow reports unsupported type, empty file and success to the user', () => {
  assert.match(pageSource, /请先选择文档空间再上传文件/)
  assert.match(pageSource, /仅支持上传 md、html、htm 文件/)
  assert.match(pageSource, /文件内容为空，请重新选择文件/)
  assert.match(pageSource, /文档上传成功/)
  assert.match(pageSource, /文档上传失败/)
  assert.doesNotMatch(pageSource, /docx/)
})

test('viewer renders markdown, sandboxed html, and plain document content by contentFormat', () => {
  assert.match(viewerSource, /MdPreview/)
  assert.match(viewerSource, /normalizedFormat\s*===\s*'markdown'/)
  assert.match(viewerSource, /normalizedFormat\s*===\s*'html'/)
  assert.match(viewerSource, /<iframe/)
  // `allow-scripts` must stay out of the sandbox so uploaded pages cannot execute JS.
  assert.doesNotMatch(viewerSource, /sandbox="[^"]*allow-scripts/)
  assert.match(viewerSource, /sandbox="allow-same-origin"/)
  assert.match(viewerSource, /:srcdoc="htmlSrcDoc"/)
  assert.doesNotMatch(viewerSource, /v-html/)
  assert.match(viewerSource, /plain-content/)
  assert.match(listSource, /<DocumentWikiContentViewer\b/)
  assert.doesNotMatch(listSource, /contentBlocks/)
})

test('markdown preview emits outline-matching heading ids and warm theme overrides', () => {
  assert.match(viewerSource, /:md-heading-id="headingId"/)
  assert.match(viewerSource, /`wiki-heading-\$\{index\}`/)
  assert.match(viewerSource, /:deep\(\.md-editor-preview-wrapper\)/)
  assert.doesNotMatch(viewerSource, /#3f3429/)
})

test('outline extraction supports markdown and html headings', () => {
  assert.match(pageSource, /extractDocumentOutline\(\s*content:\s*string,\s*contentFormat\?:\s*string/)
  assert.match(pageSource, /DOMParser/)
  assert.match(pageSource, /querySelectorAll\('h1,\s*h2,\s*h3,\s*h4'\)/)
  assert.match(pageSource, /headingPattern/)
  assert.match(pageSource, /wiki-heading-\$\{index \+ 1\}/)
})

test('outline click scrolls markdown anchors and html iframe headings', () => {
  assert.match(pageSource, /const scrollToOutline/)
  assert.match(pageSource, /document\.getElementById\(id\)/)
  assert.match(pageSource, /querySelector<HTMLIFrameElement>\('\.html-preview-frame'\)/)
  assert.match(pageSource, /frame\?\.contentDocument\?\.querySelectorAll\('h1,\s*h2,\s*h3,\s*h4'\)/)
})

test('editor stays markdown-only and no longer offers an html editor', () => {
  assert.match(editorSource, /MdEditor/)
  assert.match(editorSource, /contentFormat:\s*'markdown'/)
  assert.match(editorSource, /HTML 原页面文档本阶段仅支持预览，不支持编辑/)
  assert.doesNotMatch(editorSource, /isHtmlEditor/)
  assert.doesNotMatch(editorSource, /contenteditable/)
})

test('editor folder loader is hoisted so the immediate watcher cannot hit the TDZ', () => {
  // The immediate `watch(() => props.documentWiki)` runs during setup, so `fetchFolders` must be a
  // hoisted function declaration rather than a `const` arrow defined further down the file.
  assert.match(editorSource, /async function fetchFolders/)
  assert.doesNotMatch(editorSource, /const fetchFolders =/)
})

test('html documents are preview-only in list, workspace and detail actions', () => {
  assert.match(listSource, /isPreviewOnly/)
  assert.match(listSource, /仅预览/)
  assert.match(pageSource, /HTML 原页面文档本阶段仅支持预览，不支持编辑/)
  assert.match(detailSource, /contentFormat\s*!==\s*'html'/)
})
