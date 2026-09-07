import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const helperSource = await readFile(
  new URL('./src/pages/documentWiki/components/wikiContentRender.ts', import.meta.url),
  'utf8',
)
const listSource = await readFile(
  new URL('./src/pages/documentWiki/components/WikiDocumentList.vue', import.meta.url),
  'utf8',
)
const detailSource = await readFile(
  new URL('./src/pages/documentWiki/DocumentWikiDetailPage.vue', import.meta.url),
  'utf8',
)

test('html wiki content is sanitized before v-html rendering', () => {
  assert.match(helperSource, /sanitizeHtmlContent/)
  assert.match(helperSource, /remove\(\)/)
  assert.match(helperSource, /allowedTags/)
  assert.match(helperSource, /allowedAttributes/)
  assert.match(listSource, /v-html="renderedHtmlContent"/)
  assert.match(detailSource, /v-html="renderedHtmlContent"/)
  assert.doesNotMatch(listSource, /v-html="selectedDocument\.content"/)
  assert.doesNotMatch(detailSource, /v-html="documentWiki\.content"/)
})

test('html wiki headings receive stable outline ids', () => {
  assert.match(helperSource, /wiki-heading-\$\{headingIndex\}/)
  assert.match(helperSource, /querySelectorAll\('h1, h2, h3, h4'\)/)
  assert.match(listSource, /renderHtmlContent\(props\.selectedDocument\.content/)
  assert.match(detailSource, /renderHtmlContent\(documentWiki\.value\.content/)
})
