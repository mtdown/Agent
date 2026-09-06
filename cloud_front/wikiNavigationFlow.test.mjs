import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [headerSource, pageSource, documentListSource, routerSource] = await Promise.all([
  readSource('./src/components/GlobalHeader.vue'),
  readSource('./src/pages/documentWiki/DocumentWikiListPage.vue'),
  readSource('./src/pages/documentWiki/components/WikiDocumentList.vue'),
  readSource('./src/router/index.ts'),
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

test('normal document list reading stays inside the wiki workspace', () => {
  assert.doesNotMatch(documentListSource, /router\.push\(`\/documentWiki\/\$\{(?:item|selectedDocument)\.id\}`\)/)
  assert.match(documentListSource, /emit\('open',\s*item\.id\)/)
  assert.match(documentListSource, /router\.push\(`\/edit_documentWiki\/\$\{item\.id\}`\)/)
  assert.match(routerSource, /path:\s*'\/documentWiki\/:id'/)
})
