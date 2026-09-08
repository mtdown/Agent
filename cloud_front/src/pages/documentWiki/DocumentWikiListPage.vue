<template>
  <div id="documentWikiListPage" data-warm-page>
    <div v-if="activeRegion === 'docs'" class="wiki-shell">
      <aside class="wiki-panel wiki-tree-column">
        <WikiSpaceTree
          ref="spaceTreeRef"
          :spaces="spaces"
          @select="handleTreeSelect"
          @create-document="openCreateDocument"
          @upload-document="uploadDocument"
        />
      </aside>

      <main class="wiki-panel wiki-document-column" ref="documentColumnRef">
        <section v-if="isEditorMode" class="content-section editor-section">
          <h2>{{ centerMode === 'create' ? '新建文档' : '编辑文档' }}</h2>
          <a-spin :spinning="editorFetchLoading">
            <DocumentWikiEditor
              :key="editorKey"
              :document-wiki="editingDocument"
              :initial-space-id="editorInitialSpaceId"
              :initial-folder-id="editorInitialFolderId"
              :submit-text="centerMode === 'create' ? '创建' : '保存'"
              :loading="editorSaveLoading"
              @submit="handleEditorSubmit"
              @cancel="cancelInlineEditor"
            />
          </a-spin>
        </section>
        <section v-else class="content-section">
          <WikiSearchBar
            v-model="searchParams"
            :all-space-options="allSpaceOptions"
            @search="doSearch"
            @text-change="onSearchTextChange"
          />
          <WikiDocumentList
            :selected-document="selectedDocument"
            :is-search-mode="isSearchMode"
            :current-space-name="currentSpaceName"
            :current-folder-name="currentFolderName"
            :browse-documents="browseDocuments"
            :browse-pagination="browsePagination"
            :search-results="searchResults"
            :loading="loading"
            :pagination="pagination"
            @open="openDocument"
            @edit="openEditDocument"
            @move="moveDialogRef?.open($event)"
            @delete="deleteDocument"
          />
        </section>
      </main>

      <aside class="wiki-panel wiki-outline-column">
        <div class="panel-head">{{ outlinePanelTitle }}</div>
        <nav
          v-if="outlineMode === 'document' && documentOutline.length"
          class="outline-list"
          aria-label="本文大纲"
        >
          <button
            v-for="item in documentOutline"
            :key="item.id"
            type="button"
            class="outline-item"
            :class="`level-${item.level}`"
            @click="scrollToOutline(item.id)"
          >
            {{ item.title }}
          </button>
        </nav>
        <nav
          v-else-if="outlineMode === 'list' && folderOutlineDocs.length"
          class="outline-list"
          aria-label="文档列表"
        >
          <button
            v-for="doc in folderOutlineDocs"
            :key="doc.id"
            type="button"
            class="outline-item outline-doc-item"
            :title="doc.title"
            @click="openDocument(doc.id)"
          >
            {{ doc.title }}
          </button>
        </nav>
        <div v-else class="outline-empty">{{ outlineEmptyText }}</div>
      </aside>
    </div>
    <section v-else-if="activeRegion === 'recycle'" class="wiki-panel page-panel">
      <WikiRecyclePanel
        ref="recyclePanelRef"
        v-model:space-id="recycleSpaceId"
        :all-space-options="allSpaceOptions"
        :loading="loading"
        @restored="refreshAll"
      />
    </section>
    <section v-if="isAdmin && activeRegion === 'manage'" class="wiki-panel page-panel">
      <WikiSpaceManagePanel
        :active="activeRegion === 'manage'"
        ref="managePanelRef"
        :loading="loading"
        @changed="fetchSpaces"
      />
    </section>
    <WikiDocumentMoveDialog
      ref="moveDialogRef"
      :all-space-options="allSpaceOptions"
      @moved="refreshAll"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  addDocumentWikiUsingPost,
  deleteDocumentWikiUsingPost,
  editDocumentWikiUsingPost,
  getDocumentWikiVisByIdUsingGet,
  importDocumentWikiUsingPost,
  listDocumentWikiVisByPageWithCacheUsingPost,
} from '@/api/documentWikiController.ts'
import { listVisibleSpaceUsingGet } from '@/api/wikiSpaceController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import DocumentWikiEditor from '@/components/DocumentWikiEditor.vue'
import WikiSpaceTree, { type WikiTreeSelection } from './components/WikiSpaceTree.vue'
import WikiSearchBar from './components/WikiSearchBar.vue'
import WikiDocumentList from './components/WikiDocumentList.vue'
import WikiRecyclePanel from './components/WikiRecyclePanel.vue'
import WikiSpaceManagePanel from './components/WikiSpaceManagePanel.vue'
import WikiDocumentMoveDialog from './components/WikiDocumentMoveDialog.vue'
import { regionTitle, type IdValue } from './components/wikiShared'
import { useWikiSearch } from './components/useWikiSearch'
const { searchParams, searchResults, isSearchMode, pagination, fetchSearchResults } =
  useWikiSearch()
type RegionKey = 'docs' | 'recycle' | 'manage'
type CenterMode = 'browse' | 'preview' | 'create' | 'edit'
const recyclePanelRef = ref<InstanceType<typeof WikiRecyclePanel>>()
const managePanelRef = ref<InstanceType<typeof WikiSpaceManagePanel>>()
const moveDialogRef = ref<InstanceType<typeof WikiDocumentMoveDialog>>()
const route = useRoute()
const loginUserStore = useLoginUserStore()

const loading = ref(false)
const activeRegion = ref<RegionKey>('docs')
const centerMode = ref<CenterMode>('browse')
const previousCenterMode = ref<CenterMode>('browse')
const spaces = ref<API.WikiSpaceVis[]>([])
const browseDocuments = ref<API.DocumentWikiVis[]>([])
const browseCurrent = ref(1)
const browseTotal = ref(0)
const selectedDocument = ref<API.DocumentWikiVis>({})
const editingDocument = ref<API.DocumentWikiVis | undefined>()
const editorInitialSpaceId = ref<IdValue>()
const editorInitialFolderId = ref<IdValue | null>(null)
const editorFetchLoading = ref(false)
const editorSaveLoading = ref(false)
const recycleSpaceId = ref<IdValue>()
const spaceTreeRef = ref<InstanceType<typeof WikiSpaceTree>>()
const documentColumnRef = ref<HTMLElement>()
const currentSelection = ref<WikiTreeSelection>({
  spaceId: undefined,
  folderId: null,
  folder: null,
})

const isAdmin = computed(() => loginUserStore.loginUser?.userRole === 'admin')
const isEditorMode = computed(() => centerMode.value === 'create' || centerMode.value === 'edit')
const editorKey = computed(
  () =>
    `${centerMode.value}:${editingDocument.value?.id ?? 'new'}:${editorInitialSpaceId.value ?? ''}:${
      editorInitialFolderId.value ?? ''
    }`,
)
const allSpaceOptions = computed(() =>
  spaces.value.map((space) => ({
    label: `${regionTitle(space)} / ${space.name}`,
    value: space.id,
  })),
)
const currentSpaceName = computed(
  () =>
    spaces.value.find((space) => String(space.id) === String(currentSelection.value.spaceId))?.name,
)
const currentFolderName = computed(() => {
  const name = currentSelection.value.folder?.name
  return name ? String(name) : ''
})

// Paging for the browse list. Folder selections list that folder's own documents inline without
// paging; aggregate region (公开文档) and single-space selections page through every document
// they cover, 20 per page.
const browsePagination = computed(() => {
  if (currentSelection.value.folderId) return false
  return {
    current: browseCurrent.value,
    pageSize: 20,
    total: browseTotal.value,
    showTotal: (value: number) => `共 ${value} 条`,
    onChange: (page: number) => {
      browseCurrent.value = page
      fetchBrowsePage()
    },
  }
})
// The outline column serves two purposes: while a document is open (preview or inline edit)
// it shows that document's heading outline; whenever the middle column is listing documents
// (folder, space, or the 公开文档 aggregate) and no document is open, it mirrors that list as
// a quick-jump navigation instead of showing an empty placeholder.
const activeOutlineDocument = computed(() =>
  isEditorMode.value ? editingDocument.value : selectedDocument.value,
)
const hasActiveDocument = computed(() => Boolean(activeOutlineDocument.value?.id))
const outlineMode = computed<'document' | 'list' | 'empty'>(() => {
  if (hasActiveDocument.value) return 'document'
  if (browseDocuments.value.length) return 'list'
  return 'empty'
})
const documentOutline = computed(() => {
  const doc = activeOutlineDocument.value
  return extractDocumentOutline(doc?.content ?? '', doc?.contentFormat).map((item, index) => ({
    ...item,
    // md-editor numbers headings 1-based, so the outline must match or clicks land one heading off.
    id: `wiki-heading-${index + 1}`,
  }))
})
const folderOutlineDocs = computed(() => browseDocuments.value)
const outlinePanelTitle = computed(() => {
  if (outlineMode.value === 'document') return '本文大纲'
  const hasSelection =
    currentSelection.value.folderId ||
    currentSelection.value.spaceId ||
    currentSelection.value.spaceType != null
  if (outlineMode.value === 'list' || hasSelection) {
    return currentSelection.value.folderId ? '文件夹文档' : '文档列表'
  }
  return '本文大纲'
})
const outlineEmptyText = computed(() => {
  if (outlineMode.value === 'document') return '当前文档暂无可用大纲'
  const hasSelection =
    currentSelection.value.folderId ||
    currentSelection.value.spaceId ||
    currentSelection.value.spaceType != null
  if (hasSelection) return '当前位置暂无文档'
  return '请选择左侧文档或文件夹'
})

function extractDocumentOutline(content: string, contentFormat?: string) {
  const outline: { level: number; title: string }[] = []
  if (contentFormat === 'html') {
    const parser = new DOMParser()
    const documentValue = parser.parseFromString(content, 'text/html')
    documentValue.querySelectorAll('h1, h2, h3, h4').forEach((heading) => {
      const title = heading.textContent?.trim()
      if (!title) return
      const level = Number(heading.tagName.slice(1))
      outline.push({ level: Math.min(level, 3), title })
    })
    return outline
  }
  // Fenced code blocks are stripped first so `# comment` inside them is not mistaken for a
  // heading; the remaining headings are counted the same way md-editor numbers them, which keeps
  // the generated `wiki-heading-<index>` ids aligned with the rendered anchors.
  const withoutCode = content.replace(/```[\s\S]*?```/g, '').replace(/~~~[\s\S]*?~~~/g, '')
  const headingPattern = /^(#{1,6})\s+(.+)$/gm
  let match: RegExpExecArray | null
  while ((match = headingPattern.exec(withoutCode))) {
    outline.push({
      level: Math.min(match[1].length, 3),
      title: match[2].trim(),
    })
  }
  return outline
}

watch(
  () => route.query.open,
  (id) => {
    if (id) {
      openDocument(id as string)
    }
  },
)
watch(
  () => [route.query.region, route.query.manage, isAdmin.value] as const,
  ([region, manage, admin]) => {
    if (region === 'recycle') {
      activeRegion.value = 'recycle'
      return
    }
    if (region === 'manage' && admin) {
      activeRegion.value = 'manage'
      return
    }
    if (manage === '1' && admin) {
      activeRegion.value = 'docs'
      return
    }
    activeRegion.value = 'docs'
  },
  { immediate: true },
)

const refreshAll = async () => {
  loading.value = true
  try {
    await fetchSpaces()
    await spaceTreeRef.value?.refresh()
    if (isSearchMode.value) {
      await fetchSearchResults()
    }
    if (activeRegion.value === 'recycle' && recycleSpaceId.value) {
      await recyclePanelRef.value?.refresh()
    }
    if (activeRegion.value === 'manage' && isAdmin.value) {
      await managePanelRef.value?.refresh()
    }
  } finally {
    loading.value = false
  }
}

const fetchSpaces = async () => {
  const res = await listVisibleSpaceUsingGet()
  if (res.data.code !== 0) {
    message.error('获取文档空间失败，' + res.data.message)
    return
  }
  spaces.value = res.data.data ?? []
}

const handleTreeSelect = async (selection: WikiTreeSelection) => {
  currentSelection.value = selection
  centerMode.value = 'browse'
  selectedDocument.value = {}
  browseCurrent.value = 1
  if (!isSearchMode.value) {
    await refreshBrowseDocuments()
  }
}

const refreshBrowseDocuments = async () => {
  const { spaceId, folderId, folder, spaceType } = currentSelection.value
  // Folder selection lists that folder's own documents inline (usually few, no paging needed).
  if (folderId) {
    browseDocuments.value = folder?.documents ?? []
    browseTotal.value = browseDocuments.value.length
    return
  }
  // Aggregate region (公开文档) or a single space pages through every document it covers.
  if (spaceType != null || spaceId) {
    await fetchBrowsePage()
    return
  }
  browseDocuments.value = []
  browseTotal.value = 0
}

// Pages every document covered by the current selection: a single space recurses into all of its
// folders (spaceId without folderId), the 公开文档 aggregate spans every visible public space
// (spaceType without spaceId). Both are served by the cached paged list endpoint.
const fetchBrowsePage = async () => {
  const { spaceId, spaceType } = currentSelection.value
  const res = await listDocumentWikiVisByPageWithCacheUsingPost({
    current: browseCurrent.value,
    pageSize: 20,
    sortField: 'editTime',
    sortOrder: 'descend',
    spaceId: spaceId ?? undefined,
    spaceType: spaceType ?? undefined,
  })
  if (res.data.code === 0 && res.data.data) {
    browseDocuments.value = res.data.data.records ?? []
    browseTotal.value = Number(res.data.data.total ?? 0)
  } else {
    message.error('获取文档列表失败，' + res.data.message)
  }
}

const onSearchTextChange = () => {
  if (!searchParams.value.searchText) {
    refreshBrowseDocuments()
  }
}

const doSearch = () => {
  centerMode.value = 'browse'
  selectedDocument.value = {}
  if (!isSearchMode.value) {
    refreshBrowseDocuments()
    return
  }
  searchParams.value.current = 1
  fetchSearchResults()
}

const scrollToOutline = (id: string) => {
  // HTML documents live inside a sandboxed iframe, so their headings are addressed by position
  // through the frame document instead of by id inside the host page. Outline ids are 1-based.
  if (selectedDocument.value.contentFormat === 'html') {
    const index = Number(id.replace('wiki-heading-', '')) - 1
    const frame = document.querySelector<HTMLIFrameElement>('.html-preview-frame')
    const target = frame?.contentDocument?.querySelectorAll('h1, h2, h3, h4')[index]
    target?.scrollIntoView({ block: 'start', behavior: 'smooth' })
    return
  }
  document.getElementById(id)?.scrollIntoView({ block: 'start', behavior: 'smooth' })
}

const openDocument = async (id: IdValue) => {
  if (!id) return
  const res = await getDocumentWikiVisByIdUsingGet({ id: String(id) })
  if (res.data.code === 0 && res.data.data) {
    selectedDocument.value = res.data.data
    centerMode.value = 'preview'
  } else {
    message.error('打开文档失败，' + res.data.message)
  }
}

const openCreateDocument = (selection: WikiTreeSelection) => {
  currentSelection.value = selection
  previousCenterMode.value = centerMode.value === 'create' || centerMode.value === 'edit' ? 'browse' : centerMode.value
  editingDocument.value = undefined
  editorInitialSpaceId.value = selection.spaceId
  editorInitialFolderId.value = selection.folderId ?? null
  selectedDocument.value = {}
  centerMode.value = 'create'
}

const uploadDocument = async (selection: WikiTreeSelection, file: File) => {
  if (!selection.spaceId) {
    message.warning('请先选择文档空间再上传文件')
    return
  }
  const supportedPattern = /\.(md|html|htm)$/i
  if (!supportedPattern.test(file.name)) {
    message.error('仅支持上传 md、html、htm 文件')
    return
  }
  if (file.size === 0) {
    message.error('文件内容为空，请重新选择文件')
    return
  }
  loading.value = true
  try {
    const res = await importDocumentWikiUsingPost(
      {
        spaceId: selection.spaceId,
        folderId: selection.folderId ?? undefined,
      },
      {},
      file,
    )
    if (res.data.code === 0 && res.data.data) {
      message.success('文档上传成功')
      currentSelection.value = selection
      await refreshWorkspaceAfterSave(res.data.data, selection.spaceId)
    } else {
      message.error('文档上传失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('文档上传失败，' + e.message)
  } finally {
    loading.value = false
  }
}

const openEditDocument = async (documentWiki: API.DocumentWikiVis) => {
  const id = documentWiki.id
  if (!id) return
  if (documentWiki.contentFormat === 'html') {
    message.info('HTML 原页面文档本阶段仅支持预览，不支持编辑')
    return
  }
  previousCenterMode.value = centerMode.value === 'create' || centerMode.value === 'edit' ? 'preview' : centerMode.value
  editorFetchLoading.value = true
  try {
    const res = await getDocumentWikiVisByIdUsingGet({ id: String(id) })
    if (res.data.code === 0 && res.data.data) {
      editingDocument.value = res.data.data
      editorInitialSpaceId.value = res.data.data.spaceId
      editorInitialFolderId.value = res.data.data.folderId ?? null
      selectedDocument.value = res.data.data
      centerMode.value = 'edit'
    } else {
      message.error('获取文档失败，' + res.data.message)
    }
  } finally {
    editorFetchLoading.value = false
  }
}

const refreshWorkspaceAfterSave = async (documentId: IdValue, spaceId?: IdValue) => {
  await fetchSpaces()
  await spaceTreeRef.value?.refresh(spaceId)
  if (isSearchMode.value) {
    await fetchSearchResults()
  } else {
    await refreshBrowseDocuments()
  }
  await openDocument(documentId)
}

const handleEditorSubmit = async (values: API.DocumentWikiEditRequest) => {
  editorSaveLoading.value = true
  try {
    if (centerMode.value === 'create') {
      const res = await addDocumentWikiUsingPost(values)
      if (res.data.code === 0 && res.data.data) {
        message.success('创建成功')
        editingDocument.value = undefined
        await refreshWorkspaceAfterSave(res.data.data, values.spaceId)
      } else {
        message.error('创建失败，' + res.data.message)
      }
      return
    }
    const id = editingDocument.value?.id ?? values.id
    if (!id) return
    const res = await editDocumentWikiUsingPost({
      ...values,
      id,
    })
    if (res.data.code === 0) {
      message.success('保存成功')
      await refreshWorkspaceAfterSave(id, values.spaceId)
    } else {
      message.error('保存失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('保存失败，' + e.message)
  } finally {
    editorSaveLoading.value = false
  }
}

const cancelInlineEditor = () => {
  editingDocument.value = undefined
  centerMode.value = selectedDocument.value.id ? 'preview' : previousCenterMode.value
}

const deleteDocument = async (documentWiki: API.DocumentWikiVis) => {
  if (!documentWiki.id) return
  Modal.confirm({
    title: '删除后将进入回收站，确认删除？',
    async onOk() {
      const res = await deleteDocumentWikiUsingPost({ id: documentWiki.id })
      if (res.data.code === 0) {
        message.success('文档已删除')
        selectedDocument.value = {}
        await spaceTreeRef.value?.refresh(documentWiki.spaceId)
      } else {
        message.error('删除文档失败，' + res.data.message)
      }
    },
  })
}

onMounted(async () => {
  await fetchSpaces()
  if (route.query.open) {
    await openDocument(route.query.open as string)
  }
})
</script>
<style scoped>
#documentWikiListPage {
  min-height: calc(100vh - 106px);
  padding: 16px;
  background: var(--wiki-bg);
  color: var(--wiki-text);
}

.wiki-shell {
  display: grid;
  grid-template-columns: minmax(230px, 280px) minmax(0, 1fr) minmax(180px, 220px);
  gap: 16px;
  align-items: stretch;
  height: calc(100vh - 138px);
  min-height: 360px;
}

.wiki-panel {
  min-width: 0;
  background: var(--wiki-panel);
  border: 1px solid var(--wiki-border);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.wiki-tree-column,
.wiki-document-column,
.wiki-outline-column {
  height: 100%;
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--wiki-accent) var(--wiki-muted);
}

.wiki-tree-column::-webkit-scrollbar,
.wiki-document-column::-webkit-scrollbar,
.wiki-outline-column::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.wiki-tree-column::-webkit-scrollbar-thumb,
.wiki-document-column::-webkit-scrollbar-thumb,
.wiki-outline-column::-webkit-scrollbar-thumb {
  background: var(--wiki-accent);
  border-radius: 999px;
}

.panel-head {
  min-height: 46px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  border-bottom: 1px solid var(--wiki-border);
  background: var(--wiki-panel-head);
  border-radius: 6px 6px 0 0;
  font-weight: 600;
  flex-shrink: 0;
}

.wiki-tree-column {
  padding-bottom: 12px;
}

.wiki-tree-column :deep(.wiki-space-tree) {
  padding: 12px;
  background: transparent;
}

.wiki-document-column {
  padding: 18px 22px 24px;
}

.content-section,
.manage-section,
.page-panel {
  min-width: 0;
}

.page-panel {
  padding: 18px 22px 24px;
}

.outline-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px;
}

.outline-item {
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: var(--wiki-text-muted);
  cursor: pointer;
  min-height: 32px;
  padding: 6px 8px;
  text-align: left;
}

.outline-item:hover {
  color: #a14f16;
  background: var(--wiki-accent-soft);
  border-left-color: var(--wiki-accent);
}

.outline-item.level-2 {
  padding-left: 18px;
}

.outline-item.level-3 {
  padding-left: 28px;
  font-size: 13px;
}

.outline-doc-item {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--wiki-text);
}

.outline-empty {
  margin: 12px;
  padding: 12px;
  border-radius: 4px;
  background: var(--wiki-muted);
  color: var(--wiki-text-muted);
  line-height: 1.6;
}

@media (max-width: 900px) {
  .wiki-shell {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .wiki-tree-column,
  .wiki-document-column,
  .wiki-outline-column {
    height: auto;
    max-height: none;
  }
}
</style>
