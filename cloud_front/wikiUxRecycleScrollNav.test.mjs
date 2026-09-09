import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [recycleSource, pageSource, treeSource, documentListSource] = await Promise.all([
  readSource('./src/pages/documentWiki/components/WikiRecyclePanel.vue'),
  readSource('./src/pages/documentWiki/DocumentWikiListPage.vue'),
  readSource('./src/pages/documentWiki/components/WikiSpaceTree.vue'),
  readSource('./src/pages/documentWiki/components/WikiDocumentList.vue'),
])

// --- 回收站批量永久删除 -----------------------------------------------------------------

test('recycle panel exposes a manage mode with checkboxes and a batch action bar', () => {
  assert.match(recycleSource, /const manageMode = ref\(false\)/)
  assert.match(recycleSource, /enterManageMode/)
  assert.match(recycleSource, /exitManageMode/)
  assert.match(recycleSource, /v-if="manageMode" #avatar/)
  assert.match(recycleSource, /v-if="manageMode" class="recycle-manage-bar"/)
  assert.match(recycleSource, /批量永久删除/)
  assert.match(recycleSource, /已选 \{\{ checkedKeys\.length \}\} 项/)
})

test('recycle selection keys are composite to avoid doc/folder id collisions', () => {
  assert.match(recycleSource, /itemKey = \(item: API\.WikiRecycleItemVis\) => `\$\{item\.itemType\}:\$\{item\.itemId\}`/)
})

test('recycle batch delete loops the single permanent-delete endpoint with per-item accounting', () => {
  const body = recycleSource.slice(
    recycleSource.indexOf('const batchPermanentDelete'),
    recycleSource.indexOf('// 切换空间与手动刷新'),
  )
  assert.match(body, /permanentDeleteRecycleItemUsingPost/)
  assert.match(body, /confirm: true/)
  assert.match(body, /success \+= 1/)
  assert.match(body, /failed \+= 1/)
  assert.match(body, /refreshRecycle\(\)/)
})

test('recycle batch delete refuses to run with an empty selection', () => {
  const body = recycleSource.slice(
    recycleSource.indexOf('const batchPermanentDelete'),
    recycleSource.indexOf('// 切换空间与手动刷新'),
  )
  assert.match(body, /if \(!checkedKeys\.value\.length\)/)
  assert.match(body, /请先选择要删除的条目/)
})

test('recycle manage state is reset when the space changes or the list refreshes', () => {
  const body = recycleSource.slice(
    recycleSource.indexOf('const refreshRecycle'),
    recycleSource.indexOf('const restoreItem'),
  )
  assert.match(body, /resetManageState\(\)/)
  assert.match(body, /await fetchRecycleItems\(\)/)
  assert.match(recycleSource, /watch\(recycleSpaceId, refreshRecycle/)
})

test('recycle single-item refresh prunes stale selection keys without exiting manage mode', () => {
  assert.match(
    recycleSource,
    /checkedKeys\.value = checkedKeys\.value\.filter\(\(key\) =>[\s\S]*?recycleItems\.value\.some\(\(item\) => itemKey\(item\) === key\)/,
  )
})

// --- 列表态布局：一屏三栏、左右各自滚动，中栏仅摘要列表内滚 + 分页 15 -----------------------

test('wiki shell switches to the structured browse layout only in browse mode', () => {
  assert.match(pageSource, /:class="\{ 'wiki-shell--browse': centerMode === 'browse' \}"/)
  // 浏览态沿用固定一屏 shell：不再有 height: auto / sticky 等流式或吸顶规则。
  assert.doesNotMatch(pageSource, /\.wiki-shell--browse \{/)
  assert.doesNotMatch(pageSource, /position: sticky/)
})

test('browse middle column freezes itself and scrolls only the summary list', () => {
  const browse = pageSource.slice(pageSource.indexOf('.wiki-shell--browse .wiki-document-column'))
  assert.match(browse, /overflow: hidden/)
  assert.match(pageSource, /\.wiki-shell--browse \.content-section \{[\s\S]*?flex: 1/)
  assert.match(pageSource, /\.wiki-shell--browse \.content-section \{[\s\S]*?flex-direction: column/)
  assert.match(pageSource, /\.wiki-shell--browse \.content-section > :deep\(\.search-form\) \{[\s\S]*?flex-shrink: 0/)
})

test('document list keeps the location bar fixed and scrolls inside the spin container', () => {
  assert.match(documentListSource, /class="browse-list-body"/)
  assert.match(documentListSource, /\.browse-list-body \{[\s\S]*?flex-direction: column/)
  assert.match(
    documentListSource,
    /\.browse-list-body :deep\(\.ant-list \.ant-spin-nested-loading\) \{[\s\S]*?overflow: auto/,
  )
  assert.match(documentListSource, /\.location-bar \{[\s\S]*?flex-shrink: 0/)
})

test('browse list pages at 15 documents per page', () => {
  assert.match(pageSource, /const BROWSE_PAGE_SIZE = 15/)
})

test('column scrollbars stay invisible until the column is hovered', () => {
  // 默认透明（scrollbar-color 与 WebKit 滑块均为 transparent），hover 才染暖色。
  const cols = pageSource.slice(pageSource.indexOf('.wiki-tree-column,\n.wiki-document-column'))
  assert.match(cols, /scrollbar-color: transparent transparent/)
  assert.match(cols, /scrollbar-color: var\(--wiki-accent\) var\(--wiki-muted\)/)
  assert.match(cols, /:hover::-webkit-scrollbar-thumb[\s\S]*?background: var\(--wiki-accent\)/)
  // 中栏列表数据区独立滚动条也走同一规则。
  assert.match(
    documentListSource,
    /\.browse-list-body :deep\(\.ant-list \.ant-spin-nested-loading\) \{[\s\S]*?scrollbar-color: transparent transparent/,
  )
})

test('location bar exposes a page-jump input wired to the parent', () => {
  assert.match(documentListSource, /v-model:value="pageInput"/)
  assert.match(documentListSource, /placeholder="页码"/)
  assert.match(documentListSource, /@press-enter="confirmJump"/)
  assert.match(documentListSource, /a-button size="small" :disabled="!pageInput" @click="confirmJump"/)
  assert.match(documentListSource, /const maxPage = computed/)
  assert.match(documentListSource, /emit\('jumpToPage', target\)/)
  // 父组件处理：更新 browseCurrent + 复用 resetManageState + fetchBrowsePage。
  assert.match(pageSource, /@jump-to-page="handlePageJump"/)
  assert.match(pageSource, /const handlePageJump = \(page: number\) => \{/)
  assert.match(pageSource, /resetManageState\(\)/)
  assert.match(pageSource, /fetchBrowsePage\(\)/)
})

test('fixed-height shell rules for preview and edit modes are preserved', () => {
  assert.match(pageSource, /\.wiki-shell \{[\s\S]*?height: calc\(100vh - 138px\)/)
  assert.match(
    pageSource,
    /\.wiki-tree-column,\n\.wiki-document-column,\n\.wiki-outline-column \{[\s\S]*?overflow: auto/,
  )
})

// --- 空间导航合并单节点 -------------------------------------------------------------------

test('single-space groups collapse into one selectable root labelled with the group title', () => {
  assert.match(treeSource, /const singleSpaceRootNode = \(space: API\.WikiSpaceVis, label: string\)/)
  assert.match(treeSource, /singleSpaceRootNode\(publicSpaces\.value\[0\], '公开文档'\)/)
  assert.match(treeSource, /singleSpaceRootNode\(personalGroup\.spaces\[0\], personalGroup\.label\)/)
  // 合并节点仍是空间节点：key/nodeType 不变，选中与文件夹挂载逻辑免改。
  assert.match(
    treeSource,
    /const singleSpaceRootNode[\s\S]*?\.\.\.buildSpaceNode\(space\),\s*\n\s*label,/,
  )
})

test('multi-space fallback keeps the grouped two-level tree structure', () => {
  assert.match(treeSource, /else if \(publicSpaces\.value\.length > 1\)/)
  assert.match(treeSource, /aggregateSpaceType: 2/)
  assert.match(treeSource, /personalGroup && personalGroup\.spaces\.length > 1/)
})

test('team group is never merged away', () => {
  const teamBlock = treeSource.slice(
    treeSource.indexOf("group.key === 'group:team'"),
    treeSource.indexOf("group.key === 'group:personal'"),
  )
  assert.match(teamBlock, /nodeType: 'group'/)
  assert.doesNotMatch(teamBlock, /singleSpaceRootNode/)
})

test('first-load expansion only references group nodes that still exist', () => {
  assert.match(treeSource, /nodeType === 'group'[\s\S]*?\.map\(\(node\) => node\.key\)/)
})
