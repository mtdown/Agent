import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [headerSource, routerSource, pageSource, apiSource, typingsSource] = await Promise.all([
  readSource('./src/components/GlobalHeader.vue'),
  readSource('./src/router/index.ts'),
  readSource('./src/pages/documentWiki/DocumentWikiBatchImportPage.vue'),
  readSource('./src/api/documentWikiController.ts'),
  readSource('./src/api/typings.d.ts'),
])

test('top navigation exposes batch documents to authenticated users only', () => {
  assert.match(headerSource, /label:\s*'批量文档'/)
  assert.match(headerSource, /key:\s*'\/documentWiki\/batch'/)
  assert.match(headerSource, /loginOnly:\s*true/)
  assert.match(headerSource, /menu\.loginOnly\s*&&\s*!loginUser\?\.id/)
})

test('top navigation keeps a login-only rule separate from the admin-only rule', () => {
  assert.match(headerSource, /loginOnly\?: boolean/)
  assert.match(headerSource, /adminOnly\?: boolean/)
  assert.match(headerSource, /menu\.adminOnly/)
})

test('batch documents route renders the batch import page', () => {
  assert.match(routerSource, /path:\s*'\/documentWiki\/batch'/)
  assert.match(routerSource, /DocumentWikiBatchImportPage\.vue/)
})

test('batch page offers a destination picker with an optional folder', () => {
  assert.match(pageSource, /placeholder="目标文档空间"/)
  assert.match(pageSource, /placeholder="目标文件夹（可选，默认空间根目录）"/)
  assert.match(pageSource, /listVisibleSpaceUsingGet/)
  assert.match(pageSource, /listFolderTreeUsingGet/)
})

test('batch page accepts newline separated urls', () => {
  assert.match(pageSource, /a-textarea/)
  assert.match(pageSource, /placeholder="每行填写一个网页地址，仅支持 http \/ https"/)
  assert.match(pageSource, /split\(\/\[\\r\\n\]\+\/\)/)
  assert.match(pageSource, /batchImportUrlsUsingPost\(/)
})

test('batch page accepts multiple local documents', () => {
  assert.match(pageSource, /type="file"/)
  assert.match(pageSource, /multiple/)
  assert.match(pageSource, /accept="\.md,\.html,\.htm"/)
  assert.match(pageSource, /batchImportFilesUsingPost\(/)
})

test('batch page shows a per-item result row with status, message and document link', () => {
  assert.match(pageSource, /导入结果（成功/)
  assert.match(pageSource, /column\.key === 'status'/)
  assert.match(pageSource, /record\.status === 'SUCCESS'/)
  assert.match(pageSource, /column\.key === 'document'/)
  assert.match(pageSource, /\/documentWiki\/\$\{record\.documentId\}/)
  assert.match(pageSource, /results\.value = res\.data\.data/)
})

test('frontend api covers both batch endpoints', () => {
  assert.match(apiSource, /'\/api\/documentWiki\/batch\/url'/)
  assert.match(apiSource, /'\/api\/documentWiki\/batch\/file'/)
  assert.match(apiSource, /formData\.append\('files', file\)/)
  assert.match(typingsSource, /type BatchImportItemResult = \{/)
  assert.match(typingsSource, /type DocumentWikiBatchUrlImportRequest = \{/)
})

test('batch page keeps the warm wiki theme variables', () => {
  assert.match(pageSource, /var\(--wiki-bg\)/)
  assert.match(pageSource, /var\(--wiki-panel\)/)
  assert.doesNotMatch(pageSource, /background:\s*#fff/)
})
