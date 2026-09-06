<template>
  <div id="documentWikiListPage">
    <a-flex justify="space-between" align="center" wrap="wrap" gap="middle" class="page-header">
      <h2>Wiki 文档</h2>
      <a-space wrap>
        <a-button @click="refreshAll">刷新</a-button>
        <a-button type="primary" @click="router.push('/add_documentWiki')">创建文档</a-button>
      </a-space>
    </a-flex>

    <a-tabs v-model:active-key="activeRegion">
      <a-tab-pane key="docs" tab="文档" />
      <a-tab-pane key="recycle" tab="回收站" />
      <a-tab-pane v-if="isAdmin" key="manage" tab="文档空间管理" />
    </a-tabs>

    <div v-if="activeRegion === 'docs'" class="wiki-shell">
      <aside class="wiki-nav">
        <WikiSpaceTree ref="spaceTreeRef" :spaces="spaces" @select="handleTreeSelect" />
      </aside>

      <main class="wiki-content">
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
    </div>
    <WikiRecyclePanel
      v-else-if="activeRegion === 'recycle'"
      ref="recyclePanelRef"
      v-model:space-id="recycleSpaceId"
      :all-space-options="allSpaceOptions"
      :loading="loading"
      @restored="refreshAll"
    />
    <WikiSpaceManagePanel
      v-if="isAdmin"
      v-show="activeRegion === 'manage'"
      :active="activeRegion === 'manage'"
      ref="managePanelRef"
      :loading="loading"
      @changed="fetchSpaces"
    />
    <WikiDocumentMoveDialog
      ref="moveDialogRef"
      :all-space-options="allSpaceOptions"
      @moved="refreshAll"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
const router = useRouter()
const loginUserStore = useLoginUserStore()

const loading = ref(false)
const activeRegion = ref<RegionKey>('docs')
const spaces = ref<API.WikiSpaceVis[]>([])
const browseDocuments = ref<API.DocumentWikiVis[]>([])
const selectedDocument = ref<API.DocumentWikiVis>({})
const recycleSpaceId = ref<IdValue>()
const spaceTreeRef = ref<InstanceType<typeof WikiSpaceTree>>()
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

watch(
  () => route.query.open,
  (id) => {
    if (id) {
      openDocument(id as string)
    }
  },
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
  padding: 0 24px 24px;
}

.page-header {
  margin-bottom: 12px;
}

.wiki-shell {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.wiki-nav {
  border-right: 1px solid #f0f0f0;
  padding-right: 16px;
  min-height: 560px;
}

.wiki-content,
.content-section,
.manage-section {
  min-width: 0;
}

@media (max-width: 900px) {
  .wiki-shell {
    grid-template-columns: 1fr;
  }

  .wiki-nav {
    border-right: 0;
    border-bottom: 1px solid #f0f0f0;
    padding-right: 0;
    padding-bottom: 16px;
    min-height: 0;
  }
}
</style>
