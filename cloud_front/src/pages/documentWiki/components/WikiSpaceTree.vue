<template>
  <div class="wiki-space-tree">
    <a-flex justify="space-between" align="center" class="tree-toolbar">
      <span class="tree-title">空间导航</span>
      <a-space v-if="selectedSpaceId" size="small">
        <a-button size="small" @click="openDocumentCreator()">新建文档</a-button>
        <a-button size="small" @click="openFilePicker">上传</a-button>
        <input
          ref="fileInputRef"
          class="file-input"
          type="file"
          accept=".md,.html,.htm"
          @change="onFileChange"
        />
      </a-space>
    </a-flex>

    <a-spin :spinning="loading">
      <a-tree
        v-if="treeData.length > 0"
        :tree-data="treeData"
        :expanded-keys="expandedKeys"
        :selected-keys="selectedKeys"
        block-node
        @expand="onExpand"
        @select="onSelect"
      >
        <template #title="{ dataRef }">
          <div
            v-if="dataRef"
            class="tree-node"
            :class="{ 'group-node': dataRef.nodeType === 'group' }"
          >
            <span class="node-label" :title="dataRef.label">{{ dataRef.label }}</span>
            <a-dropdown v-if="dataRef.nodeType !== 'group'" :trigger="['click']">
              <a-button class="node-op" type="text" size="small" @click.stop>⋯</a-button>
              <template #overlay>
                <a-menu @click="onMenuClick(dataRef, $event)">
                  <a-menu-item key="create">
                    {{ dataRef.nodeType === 'folder' ? '新建子文件夹' : '新建文件夹' }}
                  </a-menu-item>
                  <template v-if="dataRef.nodeType === 'folder'">
                    <a-menu-item key="rename">重命名</a-menu-item>
                    <a-menu-item key="move">移动</a-menu-item>
                    <a-menu-item key="delete" danger>删除</a-menu-item>
                  </template>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </template>
      </a-tree>
      <a-empty v-else-if="!loading" description="暂无可见文档空间" />
    </a-spin>

    <WikiFolderDialogs
      ref="folderDialogsRef"
      :folder-trees="folderTrees"
      @changed="handleFolderChanged"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { listFolderTreeUsingGet } from '@/api/wikiFolderController.ts'
import WikiFolderDialogs from './WikiFolderDialogs.vue'

type IdValue = string | number | undefined

export type TreeNodePayload = {
  key: string
  label: string
  nodeType: 'group' | 'space' | 'folder'
  space?: API.WikiSpaceVis
  folder?: API.WikiFolderVis
  // Marks a clickable aggregate region node (e.g. "公开文档") that selects every space of a type
  // instead of a single space. Plain group nodes leave this undefined and stay unselectable.
  aggregateSpaceType?: number
  selectable?: boolean
  children?: TreeNodePayload[]
}

export type WikiTreeSelection = {
  spaceId: IdValue
  folderId?: IdValue | null
  folder?: API.WikiFolderVis | null
  // Set when an aggregate region node is selected (2 = public). The list page then pages through
  // every document across all visible spaces of that type.
  spaceType?: number | null
}

// Unified navigation tree: region groups -> spaces -> nested folders.
const props = defineProps<{ spaces: API.WikiSpaceVis[] }>()

const emit = defineEmits<{
  select: [selection: WikiTreeSelection]
  createDocument: [selection: WikiTreeSelection]
  uploadDocument: [selection: WikiTreeSelection, file: File]
}>()

const loading = ref(false)
const folderTrees = ref<Record<string, API.WikiFolderVis[]>>({})
const expandedKeys = ref<string[]>([])
const selectedKeys = ref<string[]>([])
const selectedSpaceId = ref<IdValue>()
const selectedFolderId = ref<IdValue | null>(null)
// Set when the clickable "公开文档" aggregate node is selected instead of a concrete space/folder.
const selectedAggregateType = ref<number | null>(null)
const fileInputRef = ref<HTMLInputElement>()

const folderDialogsRef = ref<InstanceType<typeof WikiFolderDialogs>>()

const publicSpaces = computed(() => props.spaces.filter((s) => s.type === 2))
const groupedSpaces = computed(() => [
  { key: 'group:team', label: '团队文档', spaces: props.spaces.filter((s) => s.type === 1) },
  { key: 'group:personal', label: '个人文档', spaces: props.spaces.filter((s) => s.type === 0) },
])

const buildSpaceNode = (space: API.WikiSpaceVis): TreeNodePayload => ({
  key: `space:${space.id}`,
  label: String(space.name ?? space.id),
  nodeType: 'space' as const,
  space,
  children: buildFolderNodes(folderTrees.value[String(space.id)] ?? [], space.id),
})

// 分组行与唯一空间行合并后的根节点：仍是一个空间节点（key 与 nodeType 不变），仅标签改用
// 分组标题——这样点击选中、文件夹挂载、依赖 selectedSpaceId 的「新建文档/上传」按钮全部免改。
const singleSpaceRootNode = (space: API.WikiSpaceVis, label: string): TreeNodePayload => ({
  ...buildSpaceNode(space),
  label,
})

const treeData = computed<TreeNodePayload[]>(() => {
  const nodes: TreeNodePayload[] = []
  // 公开空间全局唯一（ensurePublicSpace）：单空间时折叠为单行「公开文档」根节点，不再出现
  // 「公开文档 > 公开文档」两层重复；异常多空间时退回聚合格式兜底。
  if (publicSpaces.value.length === 1) {
    nodes.push(singleSpaceRootNode(publicSpaces.value[0], '公开文档'))
  } else if (publicSpaces.value.length > 1) {
    nodes.push({
      key: 'aggregate:public',
      label: '公开文档',
      nodeType: 'group',
      aggregateSpaceType: 2,
      selectable: true,
      children: publicSpaces.value.map(buildSpaceNode),
    })
  }
  // 团队空间保留「团队文档」分组：空间名各不相同（如「产品部」），分组行不冗余。
  const teamGroup = groupedSpaces.value.find((group) => group.key === 'group:team')
  if (teamGroup?.spaces.length) {
    nodes.push({
      key: teamGroup.key,
      label: teamGroup.label,
      nodeType: 'group',
      selectable: false,
      children: teamGroup.spaces.map(buildSpaceNode),
    })
  }
  // 个人空间每用户唯一（ensurePersonalSpaceForUser）：单空间时折叠为单行「个人文档」根节点。
  const personalGroup = groupedSpaces.value.find((group) => group.key === 'group:personal')
  if (personalGroup?.spaces.length === 1) {
    nodes.push(singleSpaceRootNode(personalGroup.spaces[0], personalGroup.label))
  } else if (personalGroup && personalGroup.spaces.length > 1) {
    nodes.push({
      key: personalGroup.key,
      label: personalGroup.label,
      nodeType: 'group',
      selectable: false,
      children: personalGroup.spaces.map(buildSpaceNode),
    })
  }
  return nodes
})

const buildFolderNodes = (folders: API.WikiFolderVis[], spaceId: IdValue): TreeNodePayload[] =>
  folders.map((folder) => ({
    key: `folder:${folder.id}`,
    label: String(folder.name ?? folder.id),
    nodeType: 'folder' as const,
    folder: { ...folder, spaceId: folder.spaceId ?? spaceId },
    children: buildFolderNodes(folder.children ?? [], spaceId),
  }))

const findFolderNode = (
  nodes: TreeNodePayload[],
  folderId: IdValue,
): TreeNodePayload | undefined => {
  for (const node of nodes) {
    if (node.nodeType === 'folder' && String(node.folder?.id) === String(folderId)) return node
    const hit = findFolderNode(node.children ?? [], folderId)
    if (hit) return hit
  }
  return undefined
}

const emitSelection = () => {
  if (selectedAggregateType.value != null) {
    emit('select', {
      spaceId: undefined,
      folderId: null,
      folder: null,
      spaceType: selectedAggregateType.value,
    })
    return
  }
  if (selectedFolderId.value) {
    const node = findFolderNode(treeData.value, selectedFolderId.value)
    emit('select', {
      spaceId: selectedSpaceId.value,
      folderId: selectedFolderId.value,
      folder: node?.folder ?? null,
      spaceType: null,
    })
    return
  }
  emit('select', { spaceId: selectedSpaceId.value, folderId: null, folder: null, spaceType: null })
}

const fetchSpaceTree = async (spaceId: IdValue) => {
  if (!spaceId) return
  const res = await listFolderTreeUsingGet({ spaceId })
  if (res.data.code === 0) {
    folderTrees.value[String(spaceId)] = res.data.data ?? []
  } else {
    message.error('获取文件夹失败，' + res.data.message)
  }
}

const refresh = async (spaceId?: IdValue) => {
  const targets = spaceId ? [spaceId] : props.spaces.map((s) => s.id).filter(Boolean)
  await Promise.all(targets.map((id) => fetchSpaceTree(id)))
  if (selectedFolderId.value) {
    const stillExists = findFolderNode(treeData.value, selectedFolderId.value)
    if (!stillExists) {
      selectedFolderId.value = null
      selectedKeys.value = [`space:${selectedSpaceId.value}`]
    }
  }
  emitSelection()
}

const onExpand = (keys: (string | number)[]) => {
  expandedKeys.value = keys.map(String)
}

const onSelect = (keys: (string | number)[]) => {
  if (!keys.length) {
    selectedKeys.value = [currentSelectedKey()]
    return
  }
  const key = String(keys[0])
  const node = findNodeByKey(treeData.value, key)
  if (!node) return
  // Plain group nodes (团队/个人) are not selectable; only the aggregate "公开文档" node is.
  if (node.nodeType === 'group' && node.aggregateSpaceType == null) return
  selectedKeys.value = [key]
  if (node.nodeType === 'group' && node.aggregateSpaceType != null) {
    selectedAggregateType.value = node.aggregateSpaceType
    selectedSpaceId.value = undefined
    selectedFolderId.value = null
  } else if (node.nodeType === 'space') {
    selectedAggregateType.value = null
    selectedSpaceId.value = node.space?.id
    selectedFolderId.value = null
  } else {
    selectedAggregateType.value = null
    selectedSpaceId.value = node.folder?.spaceId
    selectedFolderId.value = node.folder?.id ?? null
  }
  emitSelection()
}

const currentSelectedKey = () => {
  if (selectedAggregateType.value != null) return 'aggregate:public'
  return selectedFolderId.value ? `folder:${selectedFolderId.value}` : `space:${selectedSpaceId.value}`
}

const findNodeByKey = (nodes: TreeNodePayload[], key: string): TreeNodePayload | undefined => {
  for (const node of nodes) {
    if (node.key === key) return node
    const hit = findNodeByKey(node.children ?? [], key)
    if (hit) return hit
  }
  return undefined
}

const onNodeMenu = (action: string, node: TreeNodePayload) =>
  folderDialogsRef.value?.open(action, node)
const onMenuClick = (node: TreeNodePayload, info: { key: string | number }) =>
  onNodeMenu(String(info.key), node)
const currentSelectionPayload = (): WikiTreeSelection => {
  if (selectedFolderId.value) {
    const node = findFolderNode(treeData.value, selectedFolderId.value)
    return {
      spaceId: selectedSpaceId.value,
      folderId: selectedFolderId.value,
      folder: node?.folder ?? null,
    }
  }
  return { spaceId: selectedSpaceId.value, folderId: null, folder: null }
}
const openDocumentCreator = () => emit('createDocument', currentSelectionPayload())
const openFilePicker = () => fileInputRef.value?.click()
const onFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  emit('uploadDocument', currentSelectionPayload(), file)
  input.value = ''
}
const handleFolderChanged = async (spaceId: IdValue, parentId?: IdValue) => {
  const parentKey = parentId ? `folder:${parentId}` : `space:${spaceId}`
  if (!expandedKeys.value.includes(parentKey))
    expandedKeys.value = [...expandedKeys.value, parentKey]
  await refresh(spaceId)
}

watch(
  () => props.spaces,
  async (spaces, prev) => {
    if (!spaces.length) return
    const isFirstLoad = !prev?.length
    loading.value = true
    try {
      await refresh()
      if (isFirstLoad) {
        const firstSpace =
          publicSpaces.value[0] ??
          groupedSpaces.value.find((group) => group.spaces.length > 0)?.spaces[0]
        if (firstSpace?.id != null) {
          selectedSpaceId.value = firstSpace.id
          selectedAggregateType.value = null
          selectedKeys.value = [`space:${firstSpace.id}`]
          // Expand remaining group nodes (team / aggregate fallback) so every region label is
          // visible at a glance. Merged single-space roots carry `space:` keys, so the default
          // selection's own key already covers its merged node.
          const groupKeys = treeData.value
            .filter((node) => node.nodeType === 'group')
            .map((node) => node.key)
          expandedKeys.value = [...new Set([...groupKeys, `space:${firstSpace.id}`])]
          emitSelection()
        }
      }
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

defineExpose({ refresh })
</script>

<style scoped>
.wiki-space-tree {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tree-toolbar {
  margin-bottom: 4px;
}

.file-input {
  display: none;
}

.tree-title {
  font-weight: 600;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.group-node .node-label {
  font-weight: 600;
  color: #595959;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Kept visible at rest: the folder menu (rename / move / delete) is undiscoverable when the
   trigger only appears on hover. Dimmed slightly so it does not compete with the node label. */
.node-op {
  opacity: 0.55;
  padding: 0 4px;
}

.tree-node:hover .node-op,
.node-op:focus-visible {
  opacity: 1;
}
</style>
