<template>
  <a-modal v-model:open="folderEditorOpen" :title="folderEditorTitle" @ok="submitFolderEditor">
    <a-input
      v-model:value="folderName"
      placeholder="文件夹名称"
      @press-enter="submitFolderEditor"
    />
  </a-modal>

  <a-modal v-model:open="moveFolderOpen" title="移动文件夹" @ok="submitMoveFolder">
    <a-select
      v-model:value="moveFolderForm.parentId"
      placeholder="目标父文件夹"
      style="width: 100%"
      allow-clear
      :options="moveTargetOptions"
    />
  </a-modal>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  addFolderUsingPost,
  deleteFolderUsingPost,
  moveFolderUsingPost,
  renameFolderUsingPost,
} from '@/api/wikiFolderController.ts'
import type { IdValue } from './wikiShared'
import type { TreeNodePayload } from './WikiSpaceTree.vue'
const props = defineProps<{ folderTrees: Record<string, API.WikiFolderVis[]> }>()
const emit = defineEmits<{ changed: [spaceId: IdValue, parentId?: IdValue] }>()
const folderEditorOpen = ref(false)
const folderEditorMode = ref<'create' | 'rename'>('create')
const folderEditorTitle = ref('新建文件夹')
const folderName = ref('')
const folderParentId = ref<IdValue>()
const folderEditing = ref<API.WikiFolderVis>()
const folderSpaceId = ref<IdValue>()
const moveFolderOpen = ref(false)
const moveFolderForm = reactive<API.WikiFolderMoveRequest>({ id: undefined, parentId: undefined })
const moveTargetOptions = ref<{ label: string; value: IdValue }[]>([])

const onNodeMenu = (action: string, node: TreeNodePayload) => {
  if (node.nodeType === 'space') {
    folderSpaceId.value = node.space?.id
    if (action === 'create') openFolderEditor()
    return
  }
  const folder = node.folder
  if (!folder) return
  folderSpaceId.value = folder.spaceId
  if (action === 'create') openFolderEditor(folder.id)
  else if (action === 'rename') openRenameEditor(folder)
  else if (action === 'move') openMoveFolder(folder)
  else if (action === 'delete') deleteFolder(folder)
}

const openFolderEditor = (parentId?: IdValue) => {
  if (!folderSpaceId.value) return
  folderEditorMode.value = 'create'
  folderEditorTitle.value = parentId ? '新建子文件夹' : '新建文件夹'
  folderName.value = ''
  folderParentId.value = parentId
  folderEditing.value = undefined
  folderEditorOpen.value = true
}

const openRenameEditor = (folder: API.WikiFolderVis) => {
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
      emit('changed', folder.spaceId)
    } else {
      message.error('重命名失败，' + res.data.message)
    }
    return
  }
  const res = await addFolderUsingPost({
    spaceId: folderSpaceId.value,
    parentId: folderParentId.value,
    name: folderName.value,
  })
  if (res.data.code === 0) {
    message.success('文件夹已创建')
    folderEditorOpen.value = false
    emit('changed', folderSpaceId.value, folderParentId.value)
  } else {
    message.error('创建文件夹失败，' + res.data.message)
  }
}

const flattenFolderOptions = (
  folders: API.WikiFolderVis[],
  level = 0,
): { label: string; value: IdValue }[] =>
  folders.flatMap((folder) => [
    { label: `${'　'.repeat(level)}${folder.name}`, value: folder.id },
    ...flattenFolderOptions(folder.children ?? [], level + 1),
  ])

const openMoveFolder = (folder: API.WikiFolderVis) => {
  moveFolderForm.id = folder.id
  moveFolderForm.parentId = folder.parentId ?? ''
  const tree = props.folderTrees[String(folder.spaceId)] ?? []
  moveTargetOptions.value = [{ label: '空间根目录', value: '' }, ...flattenFolderOptions(tree)]
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
    emit('changed', folderSpaceId.value)
  } else {
    message.error('移动文件夹失败，' + res.data.message)
  }
}

const deleteFolder = (folder: API.WikiFolderVis) => {
  Modal.confirm({
    title: '删除后将进入回收站，确认删除？',
    async onOk() {
      const res = await deleteFolderUsingPost({ id: folder.id })
      if (res.data.code === 0) {
        message.success('文件夹已删除')
        emit('changed', folder.spaceId)
      } else {
        message.error('删除文件夹失败，' + res.data.message)
      }
    },
  })
}

defineExpose({ open: onNodeMenu })
</script>
