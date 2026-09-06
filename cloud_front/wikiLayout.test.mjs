import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [layoutSource, headerSource, wikiListSource] = await Promise.all([
  readSource('./src/layouts/BasicLayout.vue'),
  readSource('./src/components/GlobalHeader.vue'),
  readSource('./src/pages/documentWiki/DocumentWikiListPage.vue'),
])

test('global layout no longer renders the global sidebar', () => {
  assert.doesNotMatch(layoutSource, /<GlobalSider\b/)
  assert.doesNotMatch(layoutSource, /import\s+GlobalSider/)
})

test('top navigation contains the approved wiki layout destinations', () => {
  for (const label of [
    'WIKI文档',
    '文档创建',
    '文档空间管理',
    '回收站',
    '图库功能',
    '图片管理',
    '图片空间管理',
    '用户管理',
  ]) {
    assert.match(headerSource, new RegExp(label))
  }
  assert.match(headerSource, /background:\s*#111111/)
})

test('wiki document page exposes tree, content, and outline columns', () => {
  assert.match(wikiListSource, /class="[^"]*\bwiki-tree-column\b[^"]*"/)
  assert.match(wikiListSource, /class="[^"]*\bwiki-document-column\b[^"]*"/)
  assert.match(wikiListSource, /class="[^"]*\bwiki-outline-column\b[^"]*"/)
  assert.match(wikiListSource, /documentOutline/)
  assert.match(wikiListSource, /outline-empty/)
})
