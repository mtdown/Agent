<template>
  <a-modal v-model:open="moveDocumentOpen" :title="moveDialogTitle" @ok="submitMoveDocument">
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
// Ids being moved. A single move holds one id, a batch move holds every ticked document.
const moveTargetIds = ref<IdValue[]>([])
const moveDialogTitle = computed(() =>
  moveTargetIds.value.length > 1 ? `移动文档（${moveTargetIds.value.length} 篇）` : '移动文档',
)
const moveFolderOptions = computed(() => [
  { label: '空间根目录', value: '' },
  ...flattenFolderOptions(moveFolders.value),
])
const openMoveDocument = async (documentWiki: API.DocumentWikiVis) => {
  if (!documentWiki.id) return
  moveTargetIds.value = [documentWiki.id]
  moveDocumentForm.id = documentWiki.id
  moveDocumentForm.targetSpaceId = documentWiki.spaceId
  moveDocumentForm.targetFolderId = documentWiki.folderId ?? ''
  await loadMoveFolders(documentWiki.spaceId)
  moveDocumentOpen.value = true
}

// Batch entry used by the right-column manage mode: every ticked document is moved to the same
// target. The backend only exposes a single-document move, so the ids are applied one by one.
const openBatchMove = async (ids: IdValue[], spaceId?: IdValue) => {
  if (!ids.length) return
  moveTargetIds.value = [...ids]
  moveDocumentForm.id = ids[0]
  moveDocumentForm.targetSpaceId = spaceId
  moveDocumentForm.targetFolderId = ''
  await loadMoveFolders(spaceId)
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
  const ids = moveTargetIds.value.length ? moveTargetIds.value : [moveDocumentForm.id]
  let success = 0
  let failed = 0
  let lastError = ''
  for (const id of ids) {
    const res = await moveDocumentWikiUsingPost({
      id,
      targetSpaceId: moveDocumentForm.targetSpaceId,
      targetFolderId: moveDocumentForm.targetFolderId || undefined,
    })
    if (res.data.code === 0) {
      success += 1
    } else {
      failed += 1
      lastError = res.data.message ?? ''
    }
  }
  if (failed > 0 && success === 0) {
    message.error('移动文档失败，' + lastError)
    return
  }
  message.success(ids.length > 1 ? `已移动 ${success} 篇，失败 ${failed} 篇` : '文档已移动')
  moveDocumentOpen.value = false
  emit('moved')
}

defineExpose({ open: openMoveDocument, openBatch: openBatchMove })
</script>
