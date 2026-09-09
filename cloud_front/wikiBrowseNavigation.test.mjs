import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [pageSource, documentListSource, treeSource, moveDialogSource] = await Promise.all([
  readSource('./src/pages/documentWiki/DocumentWikiListPage.vue'),
  readSource('./src/pages/documentWiki/components/WikiDocumentList.vue'),
  readSource('./src/pages/documentWiki/components/WikiSpaceTree.vue'),
  readSource('./src/pages/documentWiki/components/WikiDocumentMoveDialog.vue'),
])

// Grabs a top-level `const <name> = ...` block so assertions stay scoped to one function
// instead of matching an identical line somewhere else in the file.
const functionBody = (name) => {
  const match = pageSource.match(
    new RegExp(`const ${name} = (?:async )?\\([\\s\\S]*?\\n\\}`),
  )
  assert.ok(match, `expected to find ${name} in DocumentWikiListPage.vue`)
  return match[0]
}

test('middle column list cards carry no action buttons', () => {
  assert.doesNotMatch(documentListSource, /<template #actions>/)
  assert.doesNotMatch(documentListSource, /@click="emit\('move', item\)"/)
  assert.doesNotMatch(documentListSource, /@click="emit\('delete', item\)"/)
  assert.doesNotMatch(documentListSource, /@click="emit\('edit', item\)"/)
  // The title stays the single way to open a document from a card.
  assert.match(documentListSource, /class="result-title" @click="emit\('open', item\.id\)"/)
})

test('preview header keeps document actions and gains a back entry', () => {
  assert.match(documentListSource, /返回列表/)
  assert.match(documentListSource, /emit\('back'\)/)
  assert.match(documentListSource, /@click="emit\('move', selectedDocument\)"/)
  assert.match(documentListSource, /@click="emit\('edit', selectedDocument\)"/)
  assert.match(documentListSource, /@click="emit\('delete', selectedDocument\)"/)
  assert.match(pageSource, /@back="goBack"/)
})

test('folder browsing queries the backend by folderId with a shared page size', () => {
  assert.match(pageSource, /const BROWSE_PAGE_SIZE = 20/)
  assert.match(pageSource, /pageSize: BROWSE_PAGE_SIZE/)
  assert.match(pageSource, /folderId: folderId \?\? undefined/)
  // Folder mode must page like the other two selections and must not read the tree cache.
  assert.doesNotMatch(pageSource, /if \(currentSelection\.value\.folderId\) return false/)
  assert.doesNotMatch(pageSource, /folder\?\.documents/)
})

test('tree selection always refreshes and leaves search mode', () => {
  const body = functionBody('handleTreeSelect')
  assert.doesNotMatch(body, /!isSearchMode/)
  assert.match(body, /searchParams\.value\.searchText = ''/)
  assert.match(body, /await refreshBrowseDocuments\(\)/)
})

test('goBack leaves the editor before returning to the list', () => {
  const body = functionBody('goBack')
  assert.match(body, /isEditorMode\.value/)
  assert.match(body, /cancelInlineEditor\(\)/)
  assert.match(body, /centerMode\.value = 'browse'/)
})

test('escape shortcut is registered, guarded and torn down', () => {
  assert.match(pageSource, /window\.addEventListener\('keydown', onKeydown\)/)
  assert.match(pageSource, /window\.removeEventListener\('keydown', onKeydown\)/)
  assert.match(pageSource, /onBeforeUnmount\(/)
  assert.match(pageSource, /event\.key !== 'Escape'/)
  assert.match(pageSource, /activeRegion\.value !== 'docs'/)
  assert.match(pageSource, /input, textarea, \[contenteditable="true"\]/)
  assert.match(pageSource, /ant-modal-wrap/)
})

test('right column exposes manage mode only in list mode', () => {
  assert.match(pageSource, /v-if="outlineMode === 'list' && folderOutlineDocs\.length"/)
  assert.match(pageSource, /manageMode \? \(allChecked \? '取消全选' : '全选'\) : '管理'/)
  assert.match(pageSource, /v-if="manageMode" class="manage-bar"/)
  assert.match(pageSource, /已选 \{\{ checkedDocIds\.length \}\} 篇/)
  assert.match(pageSource, /onOutlineDocClick/)
})

test('manage-mode row clicks tick the checkbox instead of opening', () => {
  const body = functionBody('onOutlineDocClick')
  assert.match(body, /if \(manageMode\.value\)/)
  assert.match(body, /toggleChecked\(id\)/)
  assert.match(body, /void openDocument\(id\)/)
})

test('select all only covers the documents on the current page', () => {
  assert.match(pageSource, /const selectableDocIds = computed\(\(\) =>/)
  assert.match(pageSource, /folderOutlineDocs\.value\.map\(\(doc\) => doc\.id\)/)
  assert.match(
    pageSource,
    /checkedDocIds\.value = allChecked\.value \? \[\] : \[\.\.\.selectableDocIds\.value\]/,
  )
})

test('checked state is reset on every location change', () => {
  for (const name of ['handleTreeSelect', 'openDocument', 'refreshAll']) {
    assert.match(functionBody(name), /resetManageState\(\)/, `${name} must reset manage state`)
  }
  assert.match(pageSource, /const resetManageState = \(\) => \{/)
  assert.match(pageSource, /manageMode\.value = false/)
  // Paging away must also drop ticks picked on the previous page.
  assert.match(pageSource, /resetManageState\(\)\s*\n\s*fetchBrowsePage\(\)/)
})

test('move dialog supports batch targets', () => {
  assert.match(moveDialogSource, /const openBatchMove = async \(ids: IdValue\[\], spaceId\?: IdValue\)/)
  assert.match(moveDialogSource, /moveTargetIds\.value = \[\.\.\.ids\]/)
  assert.match(moveDialogSource, /moveTargetIds\.value\.length > 1/)
  assert.match(moveDialogSource, /defineExpose\(\{ open: openMoveDocument, openBatch: openBatchMove \}\)/)
  assert.match(pageSource, /openBatch\(\[\.\.\.checkedDocIds\.value\], currentSelection\.value\.spaceId\)/)
})

test('navigation tree action trigger is visible without hover', () => {
  assert.match(treeSource, /\.node-op \{[\s\S]*?opacity: 0\.55/)
  assert.doesNotMatch(treeSource, /\.node-op \{\s*opacity: 0;?\s*\n/)
})

test('search space selector defaults to the current space', () => {
  assert.match(pageSource, /\(\) => currentSelection\.value\.spaceId,/)
  assert.match(pageSource, /searchParams\.value\.spaceId = spaceId/)
})
