import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [headerSource, pageSource, documentListSource, routerSource, treeSource, editorSource] =
  await Promise.all([
  readSource('./src/components/GlobalHeader.vue'),
  readSource('./src/pages/documentWiki/DocumentWikiListPage.vue'),
  readSource('./src/pages/documentWiki/components/WikiDocumentList.vue'),
  readSource('./src/router/index.ts'),
  readSource('./src/pages/documentWiki/components/WikiSpaceTree.vue'),
  readSource('./src/components/DocumentWikiEditor.vue'),
])

test('top navigation is the only wiki page-level navigation surface', () => {
  assert.match(headerSource, /label:\s*'WIKI文档'/)
  assert.match(headerSource, /label:\s*'回收站'/)
  assert.match(headerSource, /label:\s*'文档空间管理'/)
  assert.match(headerSource, /label:\s*'文档空间管理'[\s\S]*?adminOnly:\s*true/)
  assert.doesNotMatch(headerSource, /label:\s*'文档管理'/)
})

test('wiki document page no longer renders secondary page tabs', () => {
  assert.doesNotMatch(pageSource, /<a-tabs\b/)
  assert.doesNotMatch(pageSource, /<a-tab-pane\b[^>]*tab="文档"/)
  assert.doesNotMatch(pageSource, /<a-tab-pane\b[^>]*tab="回收站"/)
  assert.doesNotMatch(pageSource, /<a-tab-pane\b[^>]*tab="文档空间管理"/)
})

test('wiki document page removes duplicate local headers and page actions', () => {
  assert.doesNotMatch(pageSource, /<h2>Wiki 文档<\/h2>/)
  assert.doesNotMatch(pageSource, /<a-button[^>]*@click="refreshAll"[^>]*>刷新<\/a-button>/)
  assert.doesNotMatch(pageSource, /<a-button[^>]*@click="router\.push\('\/add_documentWiki'\)"[^>]*>创建文档<\/a-button>/)
  assert.doesNotMatch(pageSource, /<div class="panel-head">空间目录<\/div>/)
})

test('top navigation routes directly to wiki sub-interfaces', () => {
  assert.match(headerSource, /key:\s*'\/documentWiki'/)
  assert.match(headerSource, /key:\s*'\/documentWiki\?region=recycle'/)
  assert.match(headerSource, /key:\s*'\/documentWiki\?region=manage'/)
})

test('top navigation does not expose standalone document creation', () => {
  assert.doesNotMatch(headerSource, /label:\s*'文档创建'/)
  assert.doesNotMatch(headerSource, /key:\s*'\/add_documentWiki'/)
  assert.match(routerSource, /path:\s*'\/add_documentWiki'/)
  assert.match(routerSource, /path:\s*'\/edit_documentWiki\/:id'/)
})

test('normal document list reading stays inside the wiki workspace', () => {
  assert.doesNotMatch(documentListSource, /router\.push\(`\/documentWiki\/\$\{(?:item|selectedDocument)\.id\}`\)/)
  assert.match(documentListSource, /emit\('open',\s*item\.id\)/)
  assert.doesNotMatch(documentListSource, /router\.push\(`\/edit_documentWiki\/\$\{(?:item|selectedDocument)\.id\}`\)/)
  assert.match(documentListSource, /edit:\s*\[document:\s*API\.DocumentWikiVis\]/)
  assert.match(routerSource, /path:\s*'\/documentWiki\/:id'/)
})

test('space tree primary action creates documents while folder menu remains', () => {
  assert.match(treeSource, />\s*新建文档\s*<\/a-button/)
  assert.doesNotMatch(treeSource, /@click="openFolderEditor\(\)"\s*>\s*新建文件夹<\/a-button>/)
  assert.match(treeSource, /createDocument:\s*\[selection:\s*WikiTreeSelection\]/)
  assert.match(treeSource, /dataRef\.nodeType === 'folder' \? '新建子文件夹' : '新建文件夹'/)
})

test('wiki center column can render the shared editor inline', () => {
  assert.match(pageSource, /<DocumentWikiEditor\b/)
  assert.match(pageSource, /centerMode\s*=\s*ref<CenterMode>/)
  assert.match(pageSource, /addDocumentWikiUsingPost/)
  assert.match(pageSource, /editDocumentWikiUsingPost/)
  assert.match(editorSource, /initialSpaceId/)
  assert.match(editorSource, /initialFolderId/)
})
