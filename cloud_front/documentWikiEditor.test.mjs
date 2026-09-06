import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const editorSource = await readFile(new URL('./src/components/DocumentWikiEditor.vue', import.meta.url), 'utf8')

test('wiki image upload preserves snowflake space ids', () => {
  assert.doesNotMatch(editorSource, /spaceId:\s*Number\(formState\.spaceId\)/)
  assert.match(editorSource, /uploadWikiImageUsingPost\(\{\s*spaceId:\s*formState\.spaceId\s*\}/)
})
