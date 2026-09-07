import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const editorSource = await readFile(new URL('./src/components/DocumentWikiEditor.vue', import.meta.url), 'utf8')

test('wiki editor uses a wysiwyg tiptap surface instead of markdown split editor', () => {
  assert.match(editorSource, /@tiptap\/vue-3/)
  assert.match(editorSource, /EditorContent/)
  assert.doesNotMatch(editorSource, /MdEditor/)
  assert.doesNotMatch(editorSource, /md-editor-v3\/lib\/style\.css/)
})

test('wiki editor submits rich text as html content', () => {
  assert.match(editorSource, /editor\.getHTML\(\)/)
  assert.match(editorSource, /contentFormat:\s*'html'/)
})

test('wiki editor accepts an initial workspace location', () => {
  assert.match(editorSource, /initialLocation/)
  assert.match(editorSource, /formState\.spaceId\s*=\s*initialLocation\?\.spaceId/)
  assert.match(editorSource, /formState\.folderId\s*=\s*initialLocation\?\.folderId/)
})

test('wiki editor declares immediate watcher dependencies before watchers run', () => {
  assert.match(editorSource, /function setEditorContent\(/)
  assert.match(editorSource, /function applyInitialLocation\(/)
  assert.match(editorSource, /async function fetchFolders\(/)
  assert.doesNotMatch(editorSource, /const setEditorContent\s*=/)
  assert.doesNotMatch(editorSource, /const applyInitialLocation\s*=/)
  assert.doesNotMatch(editorSource, /const fetchFolders\s*=/)
})

test('wiki image upload preserves snowflake space ids', () => {
  assert.doesNotMatch(editorSource, /spaceId:\s*Number\(formState\.spaceId\)/)
  assert.match(editorSource, /uploadWikiImageUsingPost\(\{\s*spaceId:\s*formState\.spaceId\s*\}/)
})
