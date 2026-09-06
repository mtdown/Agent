<template>
  <section class="content-section">
    <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
      <h3>回收站</h3>
      <a-space wrap>
        <a-select
          v-model:value="recycleSpaceId"
          placeholder="选择空间"
          style="min-width: 200px"
          :options="allSpaceOptions"
          @change="fetchRecycleItems"
        />
        <a-button :disabled="!recycleSpaceId" @click="fetchRecycleItems">刷新回收站</a-button>
      </a-space>
    </a-flex>
    <a-list :data-source="recycleItems" :loading="loading">
      <template #renderItem="{ item }">
        <a-list-item>
          <template #actions>
            <a-button type="link" @click="restoreItem(item)">恢复</a-button>
            <a-button type="link" danger @click="permanentDeleteItem(item)">永久删除</a-button>
          </template>
          <a-list-item-meta>
            <template #title
              >{{ item.itemType === 'folder' ? '文件夹' : '文档' }}：{{ item.title }}</template
            >
            <template #description>
              删除人：{{ item.deleteUser?.userName ?? item.deleteBy ?? '-' }}，删除时间：{{
                formatTime(item.deleteTime)
              }}
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { formatTime, type IdValue } from './wikiShared'

import {
  listUsingGet as listRecycleUsingGet,
  permanentDeleteUsingPost as permanentDeleteRecycleItemUsingPost,
  restoreUsingPost as restoreRecycleItemUsingPost,
} from '@/api/wikiRecycleController.ts'
defineProps<{ allSpaceOptions: { label: string; value: IdValue }[]; loading: boolean }>()
const recycleSpaceId = defineModel<IdValue>('spaceId')
const emit = defineEmits<{ restored: [spaceId: IdValue] }>()
const recycleItems = ref<API.WikiRecycleItemVis[]>([])
const fetchRecycleItems = async () => {
  if (!recycleSpaceId.value) {
    recycleItems.value = []
    return
  }
  const res = await listRecycleUsingGet({ spaceId: recycleSpaceId.value })
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
    emit('restored', item.spaceId)
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

watch(recycleSpaceId, fetchRecycleItems, { immediate: true })
defineExpose({ refresh: fetchRecycleItems })
</script>
