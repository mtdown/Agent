<template>
  <div class="wiki-space-tree">
    <a-flex justify="space-between" align="center" class="tree-toolbar">
      <span class="tree-title">空间导航</span>
      <a-button v-if="selectedSpaceId" size="small" @click="openFolderEditor()"
        >新建文件夹</a-button
      >
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
  selectable?: boolean
  children?: TreeNodePayload[]
}

export type WikiTreeSelection = {
  spaceId: IdValue
  folderId?: IdValue | null
  folder?: API.WikiFolderVis | null
}

// Unified navigation tree: region groups -> spaces -> nested folders.
const props = defineProps<{ spaces: API.WikiSpaceVis[] }>()

const emit = defineEmits<{ select: [selection: WikiTreeSelection] }>()

const loading = ref(false)
const folderTrees = ref<Record<string, API.WikiFolderVis[]>>({})
const expandedKeys = ref<string[]>([])
const selectedKeys = ref<string[]>([])
const selectedSpaceId = ref<IdValue>()
const selectedFolderId = ref<IdValue | null>(null)

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

const treeData = computed<TreeNodePayload[]>(() => [
  ...publicSpaces.value.map(buildSpaceNode),
  ...groupedSpaces.value
    .filter((group) => group.spaces.length > 0)
    .map((group) => ({
      key: group.key,
      label: group.label,
      nodeType: 'group' as const,
      selectable: false,
      children: group.spaces.map(buildSpaceNode),
    })),
])

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
  if (selectedFolderId.value) {
    const node = findFolderNode(treeData.value, selectedFolderId.value)
    emit('select', {
      spaceId: selectedSpaceId.value,
      folderId: selectedFolderId.value,
      folder: node?.folder ?? null,
    })
    return
  }
  emit('select', { spaceId: selectedSpaceId.value, folderId: null, folder: null })
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
  if (!node || node.nodeType === 'group') return
  selectedKeys.value = [key]
  if (node.nodeType === 'space') {
    selectedSpaceId.value = node.space?.id
    selectedFolderId.value = null
  } else {
    selectedSpaceId.value = node.folder?.spaceId
    selectedFolderId.value = node.folder?.id ?? null
  }
  emitSelection()
}

const currentSelectedKey = () =>
  selectedFolderId.value ? `folder:${selectedFolderId.value}` : `space:${selectedSpaceId.value}`

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
const openFolderEditor = () =>
  onNodeMenu('create', {
    key: currentSelectedKey(),
    label: '',
    nodeType: 'space',
    space: { id: selectedSpaceId.value },
  })
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
          selectedKeys.value = [`space:${firstSpace.id}`]
          expandedKeys.value = [`space:${firstSpace.id}`]
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

.node-op {
  opacity: 0;
  padding: 0 4px;
}

.tree-node:hover .node-op,
.node-op:focus-visible {
  opacity: 1;
}
</style>
