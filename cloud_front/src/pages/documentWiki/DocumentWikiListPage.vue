<template>
  <div id="documentWikiListPage">
    <a-flex justify="space-between" align="center" wrap="wrap" gap="middle" class="page-header">
      <h2>Wiki 文档</h2>
      <a-space wrap>
        <a-button @click="refreshAll">刷新</a-button>
        <a-button type="primary" @click="router.push('/add_documentWiki')">创建文档</a-button>
      </a-space>
    </a-flex>

    <a-tabs v-model:active-key="activeRegion" @change="handleRegionChange">
      <a-tab-pane key="public" tab="公开文档" />
      <a-tab-pane key="team" tab="团队文档" />
      <a-tab-pane key="personal" tab="个人文档" />
      <a-tab-pane key="recycle" tab="回收站" />
      <a-tab-pane v-if="isAdmin" key="manage" tab="文档空间管理" />
    </a-tabs>

    <div v-if="activeRegion !== 'manage'" class="wiki-shell">
      <aside class="wiki-nav">
        <div class="space-list">
          <button
            v-for="space in regionSpaces"
            :key="String(space.id)"
            class="space-item"
            :class="{ active: sameId(activeSpaceId, space.id) }"
            @click="selectSpace(space.id)"
          >
            <span>{{ space.name }}</span>
          </button>
          <a-empty v-if="!loading && regionSpaces.length === 0" :description="emptyRegionText" />
        </div>

        <div v-if="activeSpaceId && activeRegion !== 'recycle'" class="tree-actions">
          <a-button block @click="createFolder()">新建文件夹</a-button>
        </div>

        <div v-if="activeSpaceId && activeRegion !== 'recycle'" class="tree-list">
          <button
            v-for="doc in rootDocuments"
            :key="`root-doc-${doc.id}`"
            class="tree-row document"
            @click="openDocument(doc.id)"
          >
            {{ doc.title }}
          </button>
          <div v-for="row in treeRows" :key="row.key">
            <div
              v-if="row.kind === 'folder'"
              class="tree-row folder"
              :style="{ paddingLeft: `${12 + row.level * 18}px` }"
            >
              <button class="tree-name" @click="createFolder(row.folder?.id)">{{ row.label }}</button>
              <a-dropdown>
                <a-button size="small">操作</a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item @click="createFolder(row.folder?.id)">新建子文件夹</a-menu-item>
                    <a-menu-item @click="renameFolder(row.folder)">重命名</a-menu-item>
                    <a-menu-item @click="openMoveFolder(row.folder)">移动</a-menu-item>
                    <a-menu-item danger @click="deleteFolder(row.folder)">删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
            <button
              v-else
              class="tree-row document"
              :style="{ paddingLeft: `${24 + row.level * 18}px` }"
              @click="openDocument(row.document?.id)"
            >
              {{ row.label }}
            </button>
          </div>
        </div>
      </aside>

      <main class="wiki-content">
        <section v-if="activeRegion === 'recycle'" class="content-section">
          <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
            <h3>回收站</h3>
            <a-button :disabled="!activeSpaceId" @click="fetchRecycleItems">刷新回收站</a-button>
          </a-flex>
          <a-list :data-source="recycleItems" :loading="loading">
            <template #renderItem="{ item }">
              <a-list-item>
                <template #actions>
                  <a-button type="link" @click="restoreItem(item)">恢复</a-button>
                  <a-button type="link" danger @click="permanentDeleteItem(item)">永久删除</a-button>
                </template>
                <a-list-item-meta>
                  <template #title>{{ item.itemType === 'folder' ? '文件夹' : '文档' }}：{{ item.title }}</template>
                  <template #description>
                    删除人：{{ item.deleteUser?.userName ?? item.deleteBy ?? '-' }}，删除时间：{{ formatTime(item.deleteTime) }}
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
        </section>

        <section v-else class="content-section">
          <a-form layout="inline" :model="searchParams" class="search-form" @finish="doSearch">
            <a-form-item label="关键词" name="searchText">
              <a-input v-model:value="searchParams.searchText" placeholder="搜索标题或正文" allow-clear />
            </a-form-item>
            <a-form-item label="匹配模式" name="matchMode">
              <a-radio-group v-model:value="searchParams.matchMode">
                <a-radio-button value="title">标题</a-radio-button>
                <a-radio-button value="titleOrContent">标题或正文</a-radio-button>
                <a-radio-button value="content">正文</a-radio-button>
              </a-radio-group>
            </a-form-item>
            <a-form-item label="空间" name="spaceId">
              <a-select
                v-model:value="searchParams.spaceId"
                placeholder="全部可见空间"
                style="min-width: 180px"
                allow-clear
                :options="allSpaceOptions"
              />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" html-type="submit">搜索</a-button>
            </a-form-item>
          </a-form>

          <article v-if="selectedDocument.id" class="document-preview">
            <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
              <h3>{{ selectedDocument.title }}</h3>
              <a-space wrap>
                <a-button @click="openMoveDocument(selectedDocument)">移动</a-button>
                <a-button @click="router.push(`/documentWiki/${selectedDocument.id}`)">查看</a-button>
                <a-button @click="router.push(`/edit_documentWiki/${selectedDocument.id}`)">编辑</a-button>
                <a-button danger @click="deleteDocument(selectedDocument)">删除</a-button>
              </a-space>
            </a-flex>
            <a-space wrap class="meta">
              <a-tag v-for="tag in selectedDocument.tags" :key="tag">{{ tag }}</a-tag>
              <span>作者：{{ selectedDocument.user?.userName ?? selectedDocument.userId ?? '-' }}</span>
              <span>编辑于：{{ formatTime(selectedDocument.editTime) }}</span>
            </a-space>
            <a-typography-paragraph v-if="selectedDocument.summary" type="secondary">
              {{ selectedDocument.summary }}
            </a-typography-paragraph>
            <div class="content">{{ selectedDocument.content }}</div>
          </article>

          <a-list
            v-else
            item-layout="vertical"
            :data-source="searchResults"
            :pagination="pagination"
            :loading="loading"
          >
            <template #renderItem="{ item }">
              <a-list-item>
                <template #actions>
                  <a-button type="link" @click="openDocument(item.id)">打开</a-button>
                  <a-button type="link" @click="router.push(`/documentWiki/${item.id}`)">查看</a-button>
                  <a-button type="link" @click="router.push(`/edit_documentWiki/${item.id}`)">编辑</a-button>
                  <a-button type="link" @click="openMoveDocument(item)">移动</a-button>
                  <a-button type="link" danger @click="deleteDocument(item)">删除</a-button>
                </template>
                <a-list-item-meta>
                  <template #title>
                    <button class="result-title" @click="openDocument(item.id)">{{ item.title }}</button>
                  </template>
                  <template #description>
                    <a-space wrap>
                      <a-tag v-for="tag in item.tags" :key="tag">{{ tag }}</a-tag>
                      <span>编辑于 {{ formatTime(item.editTime) }}</span>
                    </a-space>
                  </template>
                </a-list-item-meta>
                <div class="summary">{{ item.summary || '暂无摘要' }}</div>
              </a-list-item>
            </template>
          </a-list>
        </section>
      </main>
    </div>

    <section v-else class="manage-section">
      <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
        <h3>文档空间管理</h3>
        <a-space>
          <a-input v-model:value="newTeamName" placeholder="团队空间名称" />
          <a-button type="primary" @click="createTeamSpace">创建团队空间</a-button>
        </a-space>
      </a-flex>
      <a-table :data-source="manageSpaces" :pagination="false" row-key="id" :loading="loading" class="manage-table">
        <a-table-column title="名称" data-index="name" />
        <a-table-column title="状态">
          <template #default="{ record }">{{ record.isDelete === 1 ? '已删除' : '正常' }}</template>
        </a-table-column>
        <a-table-column title="操作">
          <template #default="{ record }">
            <a-space wrap>
              <a-button @click="selectManageSpace(record)">成员</a-button>
              <a-button v-if="record.isDelete === 1" @click="restoreTeamSpace(record)">恢复</a-button>
              <a-button
                v-if="record.isDelete === 1"
                danger
                @click="permanentDeleteTeamSpace(record)"
              >
                永久删除
              </a-button>
              <a-button v-else danger @click="deleteTeamSpace(record)">删除</a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>

      <div v-if="selectedManageSpaceId" class="member-panel">
        <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
          <h3>成员管理</h3>
          <a-space>
            <a-input v-model:value="memberUserId" placeholder="用户 ID" />
            <a-select v-model:value="memberRole" style="width: 120px">
              <a-select-option value="viewer">viewer</a-select-option>
              <a-select-option value="editor">editor</a-select-option>
              <a-select-option value="admin">admin</a-select-option>
            </a-select>
            <a-button @click="addMember">添加成员</a-button>
          </a-space>
        </a-flex>
        <a-list :data-source="members">
          <template #renderItem="{ item }">
            <a-list-item>
              <template #actions>
                <a-button type="link" danger @click="removeMember(item)">移除</a-button>
              </template>
              {{ item.user?.userName ?? item.userId }} / {{ item.spaceRole }}
            </a-list-item>
          </template>
        </a-list>
      </div>
    </section>

    <a-modal v-model:open="moveDocumentOpen" title="移动文档" @ok="submitMoveDocument">
      <a-space direction="vertical" style="width: 100%">
        <a-select
          v-model:value="moveDocumentForm.targetSpaceId"
          placeholder="目标空间"
          style="width: 100%"
          :options="allSpaceOptions"
          @change="loadMoveFolders"
        />
        <a-select
          v-model:value="moveDocumentForm.targetFolderId"
          placeholder="目标文件夹"
          style="width: 100%"
          allow-clear
          :options="moveFolderOptions"
        />
      </a-space>
    </a-modal>

    <a-modal v-model:open="folderEditorOpen" :title="folderEditorTitle" @ok="submitFolderEditor">
      <a-input v-model:value="folderName" placeholder="文件夹名称" />
    </a-modal>

    <a-modal v-model:open="moveFolderOpen" title="移动文件夹" @ok="submitMoveFolder">
      <a-select
        v-model:value="moveFolderForm.parentId"
        placeholder="目标父文件夹"
        style="width: 100%"
        allow-clear
        :options="folderOptionsForCurrentSpace"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  deleteDocumentWikiUsingPost,
  getDocumentWikiVisByIdUsingGet,
  listDocumentWikiVisByPageWithCacheUsingPost,
  listRootDocumentWikiUsingGet,
  moveDocumentWikiUsingPost,
} from '@/api/documentWikiController.ts'
import {
  addFolderUsingPost,
  deleteFolderUsingPost,
  listFolderTreeUsingGet,
  moveFolderUsingPost,
  renameFolderUsingPost,
} from '@/api/wikiFolderController.ts'
import {
  addTeamMemberUsingPost,
  addTeamSpaceUsingPost,
  deleteTeamSpaceUsingPost,
  listManageTeamSpacesUsingGet,
  listTeamMembersUsingGet,
  permanentDeleteTeamSpaceUsingPost,
  removeTeamMemberUsingPost,
  restoreTeamSpaceUsingPost,
  listVisibleSpaceUsingGet,
} from '@/api/wikiSpaceController.ts'
import {
  listRecycleUsingGet,
  permanentDeleteRecycleItemUsingPost,
  restoreRecycleItemUsingPost,
} from '@/api/wikiRecycleController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

type RegionKey = 'public' | 'team' | 'personal' | 'recycle' | 'manage'
type IdValue = string | number | undefined

type TreeRow = {
  key: string
  kind: 'folder' | 'document'
  level: number
  label?: string
  folder?: API.WikiFolderVis
  document?: API.DocumentWikiVis
}

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const loading = ref(false)
const activeRegion = ref<RegionKey>('public')
const activeSpaceId = ref<IdValue>()
const spaces = ref<API.WikiSpaceVis[]>([])
const folderTrees = ref<Record<string, API.WikiFolderVis[]>>({})
const rootDocumentsBySpace = ref<Record<string, API.DocumentWikiVis[]>>({})
const searchResults = ref<API.DocumentWikiVis[]>([])
const total = ref(0)
const selectedDocument = ref<API.DocumentWikiVis>({})
const recycleItems = ref<API.WikiRecycleItemVis[]>([])
const manageSpaces = ref<API.WikiSpaceVis[]>([])
const selectedManageSpaceId = ref<IdValue>()
const members = ref<API.WikiSpaceUserVis[]>([])
const newTeamName = ref('')
const memberUserId = ref('')
const memberRole = ref('editor')
const moveDocumentOpen = ref(false)
const moveFolderOpen = ref(false)
const folderEditorOpen = ref(false)
const folderEditorMode = ref<'create' | 'rename'>('create')
const folderEditorTitle = ref('新建文件夹')
const folderName = ref('')
const folderParentId = ref<IdValue>()
const folderEditing = ref<API.WikiFolderVis>()
const moveFolders = ref<API.WikiFolderVis[]>([])

const searchParams = reactive<API.DocumentWikiQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'editTime',
  sortOrder: 'descend',
  matchMode: 'titleOrContent',
})

const moveDocumentForm = reactive<API.DocumentWikiMoveRequest>({
  id: undefined,
  targetSpaceId: undefined,
  targetFolderId: undefined,
})

const moveFolderForm = reactive<API.WikiFolderMoveRequest>({
  id: undefined,
  parentId: undefined,
})

const isAdmin = computed(() => loginUserStore.loginUser?.userRole === 'admin')
const publicSpaces = computed(() => spaces.value.filter((space) => space.type === 2))
const teamSpaces = computed(() => spaces.value.filter((space) => space.type === 1))
const personalSpaces = computed(() => spaces.value.filter((space) => space.type === 0))

const regionSpaces = computed(() => {
  if (activeRegion.value === 'team') return teamSpaces.value
  if (activeRegion.value === 'personal') return personalSpaces.value
  if (activeRegion.value === 'recycle') return spaces.value
  return publicSpaces.value
})

const activeSpaceKey = computed(() => String(activeSpaceId.value ?? ''))
const currentFolders = computed(() => folderTrees.value[activeSpaceKey.value] ?? [])
const rootDocuments = computed(() => rootDocumentsBySpace.value[activeSpaceKey.value] ?? [])
const treeRows = computed(() => flattenTree(currentFolders.value))
const emptyRegionText = computed(() => (activeRegion.value === 'team' ? '暂无加入的团队文档空间' : '暂无文档空间'))
const allSpaceOptions = computed(() => spaces.value.map((space) => ({ label: `${regionTitle(space)} / ${space.name}`, value: space.id })))
const folderOptionsForCurrentSpace = computed(() => [{ label: '空间根目录', value: '' }, ...flattenFolderOptions(currentFolders.value)])
const moveFolderOptions = computed(() => [{ label: '空间根目录', value: '' }, ...flattenFolderOptions(moveFolders.value)])

const pagination = computed(() => ({
  current: searchParams.current ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showTotal: (value: number) => `共 ${value} 条`,
  onChange: (page: number, pageSize: number) => {
    searchParams.current = page
    searchParams.pageSize = pageSize
    fetchSearchResults()
  },
}))

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
    await fetchSearchResults()
    if (activeRegion.value === 'manage' && isAdmin.value) {
      await fetchManageSpaces()
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
  if (!activeSpaceId.value || !spaces.value.some((space) => sameId(space.id, activeSpaceId.value))) {
    activeSpaceId.value = regionSpaces.value[0]?.id ?? spaces.value[0]?.id
  }
  if (activeSpaceId.value) {
    await fetchSpaceTree(activeSpaceId.value)
  }
}

const fetchSpaceTree = async (spaceId: IdValue) => {
  if (!spaceId) return
  const [folderRes, rootRes] = await Promise.all([
    listFolderTreeUsingGet({ spaceId }),
    listRootDocumentWikiUsingGet({ spaceId }),
  ])
  if (folderRes.data.code === 0) {
    folderTrees.value[String(spaceId)] = folderRes.data.data ?? []
  }
  if (rootRes.data.code === 0) {
    rootDocumentsBySpace.value[String(spaceId)] = rootRes.data.data ?? []
  }
}

const selectSpace = async (spaceId: IdValue) => {
  activeSpaceId.value = spaceId
  selectedDocument.value = {}
  if (activeRegion.value === 'recycle') {
    await fetchRecycleItems()
  } else {
    await fetchSpaceTree(spaceId)
  }
}

const handleRegionChange = async () => {
  selectedDocument.value = {}
  if (activeRegion.value === 'manage') {
    await fetchManageSpaces()
    return
  }
  activeSpaceId.value = regionSpaces.value[0]?.id
  if (activeRegion.value === 'recycle') {
    await fetchRecycleItems()
  } else if (activeSpaceId.value) {
    await fetchSpaceTree(activeSpaceId.value)
  }
}

const fetchSearchResults = async () => {
  const res = await listDocumentWikiVisByPageWithCacheUsingPost({ ...searchParams })
  if (res.data.code === 0 && res.data.data) {
    searchResults.value = res.data.data.records ?? []
    total.value = Number(res.data.data.total ?? 0)
  } else {
    message.error('搜索文档失败，' + res.data.message)
  }
}

const doSearch = () => {
  selectedDocument.value = {}
  searchParams.current = 1
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

const createFolder = async (parentId?: IdValue) => {
  if (!activeSpaceId.value) return
  folderEditorMode.value = 'create'
  folderEditorTitle.value = '新建文件夹'
  folderName.value = ''
  folderParentId.value = parentId
  folderEditing.value = undefined
  folderEditorOpen.value = true
}

const renameFolder = (folder?: API.WikiFolderVis) => {
  if (!folder?.id) return
  folderEditorMode.value = 'rename'
  folderEditorTitle.value = '重命名文件夹'
  folderName.value = folder.name ?? ''
  folderEditing.value = folder
  folderEditorOpen.value = true
}

const submitFolderEditor = async () => {
  if (!folderName.value) {
    message.warning('请输入文件夹名称')
    return
  }
  if (folderEditorMode.value === 'rename') {
    const folder = folderEditing.value
    if (!folder?.id) return
    const res = await renameFolderUsingPost({ id: folder.id, name: folderName.value })
    if (res.data.code === 0) {
      message.success('文件夹已重命名')
      folderEditorOpen.value = false
      await fetchSpaceTree(folder.spaceId)
    } else {
      message.error('重命名失败，' + res.data.message)
    }
    return
  }
  const res = await addFolderUsingPost({
    spaceId: activeSpaceId.value,
    parentId: folderParentId.value,
    name: folderName.value,
  })
  if (res.data.code === 0) {
    message.success('文件夹已创建')
    folderEditorOpen.value = false
    await fetchSpaceTree(activeSpaceId.value)
  } else {
    message.error('创建文件夹失败，' + res.data.message)
  }
}

const openMoveFolder = (folder?: API.WikiFolderVis) => {
  if (!folder?.id) return
  moveFolderForm.id = folder.id
  moveFolderForm.parentId = folder.parentId ?? ''
  moveFolderOpen.value = true
}

const submitMoveFolder = async () => {
  const res = await moveFolderUsingPost({
    id: moveFolderForm.id,
    parentId: moveFolderForm.parentId || undefined,
  })
  if (res.data.code === 0) {
    message.success('文件夹已移动')
    moveFolderOpen.value = false
    await fetchSpaceTree(activeSpaceId.value)
  } else {
    message.error('移动文件夹失败，' + res.data.message)
  }
}

const deleteFolder = async (folder?: API.WikiFolderVis) => {
  if (!folder?.id) return
  Modal.confirm({
    title: '删除后将进入回收站，确认删除？',
    async onOk() {
      const res = await deleteFolderUsingPost({ id: folder.id })
      if (res.data.code === 0) {
        message.success('文件夹已删除')
        selectedDocument.value = {}
        await fetchSpaceTree(folder.spaceId)
      } else {
        message.error('删除文件夹失败，' + res.data.message)
      }
    },
  })
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
        await fetchSearchResults()
        if (documentWiki.spaceId) {
          await fetchSpaceTree(documentWiki.spaceId)
        }
      } else {
        message.error('删除文档失败，' + res.data.message)
      }
    },
  })
}

const openMoveDocument = async (documentWiki: API.DocumentWikiVis) => {
  if (!documentWiki.id) return
  moveDocumentForm.id = documentWiki.id
  moveDocumentForm.targetSpaceId = documentWiki.spaceId
  moveDocumentForm.targetFolderId = documentWiki.folderId ?? ''
  await loadMoveFolders(documentWiki.spaceId)
  moveDocumentOpen.value = true
}

const loadMoveFolders = async (spaceId: IdValue) => {
  moveDocumentForm.targetFolderId = ''
  if (!spaceId) {
    moveFolders.value = []
    return
  }
  const res = await listFolderTreeUsingGet({ spaceId })
  if (res.data.code === 0) {
    moveFolders.value = res.data.data ?? []
  }
}

const submitMoveDocument = async () => {
  const res = await moveDocumentWikiUsingPost({
    id: moveDocumentForm.id,
    targetSpaceId: moveDocumentForm.targetSpaceId,
    targetFolderId: moveDocumentForm.targetFolderId || undefined,
  })
  if (res.data.code === 0) {
    message.success('文档已移动')
    moveDocumentOpen.value = false
    await refreshAll()
  } else {
    message.error('移动文档失败，' + res.data.message)
  }
}

const fetchRecycleItems = async () => {
  if (!activeSpaceId.value) {
    recycleItems.value = []
    return
  }
  const res = await listRecycleUsingGet({ spaceId: activeSpaceId.value })
  if (res.data.code === 0) {
    recycleItems.value = res.data.data ?? []
  } else {
    message.error('获取回收站失败，' + res.data.message)
  }
}

const restoreItem = async (item: API.WikiRecycleItemVis) => {
  const res = await restoreRecycleItemUsingPost({
    spaceId: item.spaceId,
    itemId: item.itemId,
    itemType: item.itemType,
  })
  if (res.data.code === 0) {
    message.success('已恢复')
    await fetchRecycleItems()
    await fetchSpaceTree(item.spaceId)
  } else {
    message.error('恢复失败，' + res.data.message)
  }
}

const permanentDeleteItem = async (item: API.WikiRecycleItemVis) => {
  Modal.confirm({
    title: '永久删除后不可恢复，确认继续？',
    async onOk() {
      const res = await permanentDeleteRecycleItemUsingPost({
        spaceId: item.spaceId,
        itemId: item.itemId,
        itemType: item.itemType,
        confirm: true,
      })
      if (res.data.code === 0) {
        message.success('已永久删除')
        await fetchRecycleItems()
      } else {
        message.error('永久删除失败，' + res.data.message)
      }
    },
  })
}

const fetchManageSpaces = async () => {
  if (!isAdmin.value) return
  const res = await listManageTeamSpacesUsingGet()
  if (res.data.code === 0) {
    manageSpaces.value = res.data.data ?? []
  } else {
    message.error('获取团队空间失败，' + res.data.message)
  }
}

const createTeamSpace = async () => {
  if (!newTeamName.value) return
  const res = await addTeamSpaceUsingPost({ name: newTeamName.value })
  if (res.data.code === 0) {
    message.success('团队空间已创建')
    newTeamName.value = ''
    await fetchManageSpaces()
    await fetchSpaces()
  } else {
    message.error('创建团队空间失败，' + res.data.message)
  }
}

const selectManageSpace = async (space: API.WikiSpaceVis) => {
  selectedManageSpaceId.value = space.id
  const res = await listTeamMembersUsingGet({ spaceId: space.id })
  if (res.data.code === 0) {
    members.value = res.data.data ?? []
  }
}

const addMember = async () => {
  if (!selectedManageSpaceId.value || !memberUserId.value) return
  const res = await addTeamMemberUsingPost({
    spaceId: selectedManageSpaceId.value,
    userId: memberUserId.value,
    spaceRole: memberRole.value,
  })
  if (res.data.code === 0) {
    message.success('成员已添加')
    memberUserId.value = ''
    await selectManageSpace({ id: selectedManageSpaceId.value })
    await fetchSpaces()
  } else {
    message.error('添加成员失败，' + res.data.message)
  }
}

const removeMember = async (member: API.WikiSpaceUserVis) => {
  if (!selectedManageSpaceId.value || !member.userId) return
  const res = await removeTeamMemberUsingPost({ spaceId: selectedManageSpaceId.value, userId: member.userId })
  if (res.data.code === 0) {
    message.success('成员已移除')
    await selectManageSpace({ id: selectedManageSpaceId.value })
  } else {
    message.error('移除成员失败，' + res.data.message)
  }
}

const deleteTeamSpace = async (space: API.WikiSpaceVis) => {
  Modal.confirm({
    title: '确认删除团队空间？非空空间会进入管理区待恢复或永久删除。',
    async onOk() {
      const res = await deleteTeamSpaceUsingPost({ id: space.id, confirm: true })
      if (res.data.code === 0) {
        message.success('团队空间已删除')
        await fetchManageSpaces()
        await fetchSpaces()
      } else {
        message.error('删除团队空间失败，' + res.data.message)
      }
    },
  })
}

const restoreTeamSpace = async (space: API.WikiSpaceVis) => {
  const res = await restoreTeamSpaceUsingPost({ id: space.id })
  if (res.data.code === 0) {
    message.success('团队空间已恢复')
    await fetchManageSpaces()
    await fetchSpaces()
  } else {
    message.error('恢复团队空间失败，' + res.data.message)
  }
}

const permanentDeleteTeamSpace = async (space: API.WikiSpaceVis) => {
  Modal.confirm({
    title: '永久删除团队空间后不可恢复，确认继续？',
    async onOk() {
      const res = await permanentDeleteTeamSpaceUsingPost({ id: space.id, confirm: true })
      if (res.data.code === 0) {
        message.success('团队空间已永久删除')
        await fetchManageSpaces()
        await fetchSpaces()
      } else {
        message.error('永久删除团队空间失败，' + res.data.message)
      }
    },
  })
}

const flattenTree = (folders: API.WikiFolderVis[], level = 0): TreeRow[] => {
  return folders.flatMap((folder) => [
    { key: `folder-${folder.id}`, kind: 'folder' as const, level, label: folder.name, folder },
    ...(folder.documents ?? []).map((document) => ({
      key: `document-${document.id}`,
      kind: 'document' as const,
      level: level + 1,
      label: document.title,
      document,
    })),
    ...flattenTree(folder.children ?? [], level + 1),
  ])
}

const flattenFolderOptions = (
  folders: API.WikiFolderVis[],
  level = 0,
): { label: string; value: IdValue }[] => {
  return folders.flatMap((folder) => [
    { label: `${'　'.repeat(level)}${folder.name}`, value: folder.id },
    ...flattenFolderOptions(folder.children ?? [], level + 1),
  ])
}

const regionTitle = (space: API.WikiSpaceVis) => {
  if (space.type === 2) return '公开文档'
  if (space.type === 1) return '团队文档'
  return '个人文档'
}

const sameId = (left: IdValue, right: IdValue) => String(left ?? '') === String(right ?? '')

const formatTime = (time?: string) => {
  if (!time) return '-'
  const date = /^\d+$/.test(time) ? new Date(Number(time)) : new Date(time)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString()
}

onMounted(async () => {
  await refreshAll()
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

.space-list,
.tree-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.space-item,
.tree-row,
.result-title,
.tree-name {
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.space-item {
  padding: 8px 10px;
  border-radius: 6px;
}

.space-item.active {
  background: #e6f4ff;
  color: #0958d9;
}

.tree-actions {
  margin: 14px 0 10px;
}

.tree-row {
  width: 100%;
  min-height: 32px;
  padding: 5px 8px;
  border-radius: 6px;
}

.folder {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fafafa;
}

.document:hover,
.result-title:hover,
.tree-name:hover {
  color: #1677ff;
}

.wiki-content,
.content-section,
.manage-section {
  min-width: 0;
}

.search-form {
  margin-bottom: 16px;
  row-gap: 12px;
}

.document-preview {
  border-top: 1px solid #f0f0f0;
  padding-top: 16px;
}

.meta {
  margin-bottom: 14px;
}

.summary {
  color: #666;
  white-space: pre-wrap;
}

.content {
  line-height: 1.8;
  white-space: pre-wrap;
}

.manage-table,
.member-panel {
  margin-top: 16px;
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
