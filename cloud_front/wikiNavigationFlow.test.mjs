import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [headerSource, pageSource, documentListSource, routerSource, searchBarSource] = await Promise.all([
  readSource('./src/components/GlobalHeader.vue'),
  readSource('./src/pages/documentWiki/DocumentWikiListPage.vue'),
  readSource('./src/pages/documentWiki/components/WikiDocumentList.vue'),
  readSource('./src/router/index.ts'),
  readSource('./src/pages/documentWiki/components/WikiSearchBar.vue'),
])

test('top navigation is the only wiki page-level navigation surface', () => {
  assert.match(headerSource, /label:\s*'WIKI文档'/)
  assert.match(headerSource, /label:\s*'回收站'/)
  assert.match(headerSource, /label:\s*'文档空间管理'/)
  assert.match(headerSource, /label:\s*'文档空间管理'[\s\S]*?adminOnly:\s*true/)
  assert.doesNotMatch(headerSource, /label:\s*'文档管理'/)
  assert.doesNotMatch(headerSource, /label:\s*'文档创建'/)
  assert.doesNotMatch(headerSource, /FileAddOutlined/)
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

test('wiki search bar exposes workspace document creation next to search', () => {
  assert.match(searchBarSource, /emit\('create'\)/)
  assert.match(searchBarSource, /html-type="button"[\s\S]*@click="emit\('create'\)/)
  assert.match(searchBarSource, />搜索<\/a-button>[\s\S]*?>创建文档<\/a-button>/)
})

test('wiki document creation stays inside the workspace center column', () => {
  const modeWatcherStart = pageSource.indexOf('() => route.query.mode')
  const nextWatcherStart = pageSource.indexOf('watch(', modeWatcherStart + 1)
  const modeWatcherSource = pageSource.slice(modeWatcherStart, nextWatcherStart)

  assert.match(pageSource, /centerMode/)
  assert.match(pageSource, /centerMode\s*===\s*'create'/)
  assert.match(pageSource, /<DocumentWikiEditor/)
  assert.match(pageSource, /:initial-location=/)
  assert.match(pageSource, /@create=/)
  assert.match(pageSource, /ensureCurrentSelection\(\)/)
  assert.notEqual(modeWatcherStart, -1)
  assert.notEqual(nextWatcherStart, -1)
  assert.doesNotMatch(modeWatcherSource, /immediate:\s*true/)
  assert.doesNotMatch(pageSource, /router\.push\('\/add_documentWiki'\)/)
})

test('wiki region panes are a single exclusive render branch', () => {
  assert.match(pageSource, /<div v-if="activeRegion === 'docs'"/)
  assert.match(pageSource, /<section v-else-if="activeRegion === 'recycle'"/)
  assert.match(pageSource, /<section v-else-if="isAdmin && activeRegion === 'manage'"/)
  assert.doesNotMatch(pageSource, /<section v-if="isAdmin && activeRegion === 'manage'"/)
})

test('normal document list reading stays inside the wiki workspace', () => {
  assert.doesNotMatch(documentListSource, /router\.push\(`\/documentWiki\/\$\{(?:item|selectedDocument)\.id\}`\)/)
  assert.match(documentListSource, /emit\('open',\s*item\.id\)/)
  assert.match(documentListSource, /router\.push\(`\/edit_documentWiki\/\$\{item\.id\}`\)/)
  assert.match(routerSource, /path:\s*'\/documentWiki\/:id'/)
})
