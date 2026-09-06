<template>
  <div id="documentWikiListPage">
    <div v-if="activeRegion === 'docs'" class="wiki-shell">
      <aside class="wiki-panel wiki-tree-column">
        <WikiSpaceTree ref="spaceTreeRef" :spaces="spaces" @select="handleTreeSelect" />
      </aside>

      <main class="wiki-panel wiki-document-column" ref="documentColumnRef">
        <section class="content-section">
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
            :search-results="searchResults"
            :loading="loading"
            :pagination="pagination"
            @open="openDocument"
            @move="moveDialogRef?.open($event)"
            @delete="deleteDocument"
          />
        </section>
      </main>

      <aside class="wiki-panel wiki-outline-column">
        <div class="panel-head">本文大纲</div>
        <nav v-if="documentOutline.length" class="outline-list" aria-label="本文大纲">
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
        <div v-else class="outline-empty">当前文档暂无可用大纲</div>
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
  deleteDocumentWikiUsingPost,
  getDocumentWikiVisByIdUsingGet,
  listRootDocumentWikiUsingGet,
} from '@/api/documentWikiController.ts'
import { listVisibleSpaceUsingGet } from '@/api/wikiSpaceController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
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
const recyclePanelRef = ref<InstanceType<typeof WikiRecyclePanel>>()
const managePanelRef = ref<InstanceType<typeof WikiSpaceManagePanel>>()
const moveDialogRef = ref<InstanceType<typeof WikiDocumentMoveDialog>>()
const route = useRoute()
const loginUserStore = useLoginUserStore()

const loading = ref(false)
const activeRegion = ref<RegionKey>('docs')
const spaces = ref<API.WikiSpaceVis[]>([])
const browseDocuments = ref<API.DocumentWikiVis[]>([])
const selectedDocument = ref<API.DocumentWikiVis>({})
const recycleSpaceId = ref<IdValue>()
const spaceTreeRef = ref<InstanceType<typeof WikiSpaceTree>>()
const documentColumnRef = ref<HTMLElement>()
const currentSelection = ref<WikiTreeSelection>({
  spaceId: undefined,
  folderId: null,
  folder: null,
})

const isAdmin = computed(() => loginUserStore.loginUser?.userRole === 'admin')
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
const documentOutline = computed(() =>
  extractDocumentOutline(selectedDocument.value.content ?? '').map((item, index) => ({
    ...item,
    id: `wiki-heading-${index}`,
  })),
)

const extractDocumentOutline = (content: string) => {
  const outline: { level: number; title: string }[] = []
  const headingPattern = /^(#{1,4})\s+(.+)$/gm
  let match: RegExpExecArray | null
  while ((match = headingPattern.exec(content))) {
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
  selectedDocument.value = {}
  if (!isSearchMode.value) {
    await refreshBrowseDocuments()
  }
}

const refreshBrowseDocuments = async () => {
  const { spaceId, folderId, folder } = currentSelection.value
  if (!spaceId) {
    browseDocuments.value = []
    return
  }
  if (folderId) {
    browseDocuments.value = folder?.documents ?? []
    return
  }
  const res = await listRootDocumentWikiUsingGet({ spaceId })
  if (res.data.code === 0) {
    browseDocuments.value = res.data.data ?? []
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
  selectedDocument.value = {}
  if (!isSearchMode.value) {
    refreshBrowseDocuments()
    return
  }
  searchParams.value.current = 1
  fetchSearchResults()
}

const scrollToOutline = (id: string) => {
  document.getElementById(id)?.scrollIntoView({ block: 'start', behavior: 'smooth' })
}

const openDocument = async (id: IdValue) => {
  if (!id) return
  const res = await getDocumentWikiVisByIdUsingGet({ id: String(id) })
  if (res.data.code === 0 && res.data.data) {
    selectedDocument.value = res.data.data
  } else {
    message.error('打开文档失败，' + res.data.message)
  }
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
  background: #f5eddf;
  color: #251f18;
}

.wiki-shell {
  display: grid;
  grid-template-columns: minmax(230px, 280px) minmax(0, 1fr) minmax(180px, 220px);
  gap: 16px;
  align-items: start;
}

.wiki-panel {
  min-width: 0;
  background: #fffaf1;
  border: 1px solid #ded0bc;
  border-radius: 6px;
}

.wiki-tree-column,
.wiki-document-column,
.wiki-outline-column {
  max-height: calc(100vh - 90px);
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: #e07a2d #ece0ce;
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
  background: #e07a2d;
  border-radius: 999px;
}

.panel-head {
  min-height: 46px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  border-bottom: 1px solid #ded0bc;
  background: #fbf3e7;
  border-radius: 6px 6px 0 0;
  font-weight: 600;
}

.wiki-tree-column {
  padding-bottom: 12px;
}

.wiki-tree-column :deep(.wiki-space-tree) {
  padding: 12px;
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
  color: #7b6c5d;
  cursor: pointer;
  min-height: 32px;
  padding: 6px 8px;
  text-align: left;
}

.outline-item:hover {
  color: #a14f16;
  background: #fff0df;
  border-left-color: #e07a2d;
}

.outline-item.level-2 {
  padding-left: 18px;
}

.outline-item.level-3 {
  padding-left: 28px;
  font-size: 13px;
}

.outline-empty {
  margin: 12px;
  padding: 12px;
  border-radius: 4px;
  background: #f1eadf;
  color: #7b6c5d;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .wiki-shell {
    grid-template-columns: 1fr;
  }

  .wiki-tree-column,
  .wiki-document-column,
  .wiki-outline-column {
    max-height: none;
  }
}
</style>
