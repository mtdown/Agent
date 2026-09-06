<template>
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
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { moveDocumentWikiUsingPost } from '@/api/documentWikiController.ts'
import { listFolderTreeUsingGet } from '@/api/wikiFolderController.ts'
import { flattenFolderOptions, type IdValue } from './wikiShared'
defineProps<{ allSpaceOptions: { label: string; value: IdValue }[] }>()
const emit = defineEmits<{ moved: [] }>()
const moveDocumentOpen = ref(false)
const moveFolders = ref<API.WikiFolderVis[]>([])
const moveDocumentForm = reactive<API.DocumentWikiMoveRequest>({})
const moveFolderOptions = computed(() => [
  { label: '空间根目录', value: '' },
  ...flattenFolderOptions(moveFolders.value),
])
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
    emit('moved')
  } else {
    message.error('移动文档失败，' + res.data.message)
  }
}

defineExpose({ open: openMoveDocument })
</script>
