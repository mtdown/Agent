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
        />
        <a-button :disabled="!recycleSpaceId" @click="refreshRecycle">刷新回收站</a-button>
        <a-button
          v-if="recycleItems.length"
          @click="manageMode ? exitManageMode() : enterManageMode()"
        >
          {{ manageMode ? '退出' : '管理' }}
        </a-button>
      </a-space>
    </a-flex>
    <a-list :data-source="recycleItems" :loading="loading">
      <template #renderItem="{ item }">
        <a-list-item :class="{ 'recycle-item-managing': manageMode }" @click="onItemClick(item)">
          <template #actions>
            <a-button type="link" @click.stop="restoreItem(item)">恢复</a-button>
            <a-button type="link" danger @click.stop="permanentDeleteItem(item)"
              >永久删除</a-button
            >
          </template>
          <a-list-item-meta>
            <template v-if="manageMode" #avatar>
              <a-checkbox
                class="recycle-item-check"
                :checked="isChecked(item)"
                @click.stop
                @change="() => toggleChecked(item)"
              />
            </template>
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
    <div v-if="manageMode" class="recycle-manage-bar">
      <span>已选 {{ checkedKeys.length }} 项</span>
      <a-space size="small">
        <a-button size="small" :disabled="!recycleItems.length" @click="toggleSelectAll">
          {{ allChecked ? '取消全选' : '全选' }}
        </a-button>
        <a-button size="small" danger @click="batchPermanentDelete">批量永久删除</a-button>
        <a-button size="small" @click="exitManageMode">取消</a-button>
      </a-space>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
    // 单条恢复/永久删除后列表会刷新，这里只剔除已不存在的勾选，保留管理态与其余勾选。
    checkedKeys.value = checkedKeys.value.filter((key) =>
      recycleItems.value.some((item) => itemKey(item) === key),
    )
  } else {
    message.error('获取回收站失败，' + res.data.message)
  }
}

// --- 批量管理态 -------------------------------------------------------------------
// 勾选键用 `${itemType}:${itemId}` 复合值：文档与文件夹的 id 各自独立，单用 id 会撞键。
const manageMode = ref(false)
const checkedKeys = ref<string[]>([])

const itemKey = (item: API.WikiRecycleItemVis) => `${item.itemType}:${item.itemId}`

const resetManageState = () => {
  manageMode.value = false
  checkedKeys.value = []
}

const enterManageMode = () => {
  manageMode.value = true
}

const exitManageMode = () => {
  resetManageState()
}

const isChecked = (item: API.WikiRecycleItemVis) => checkedKeys.value.includes(itemKey(item))

const toggleChecked = (item: API.WikiRecycleItemVis) => {
  const key = itemKey(item)
  checkedKeys.value = checkedKeys.value.includes(key)
    ? checkedKeys.value.filter((value) => value !== key)
    : [...checkedKeys.value, key]
}

const allChecked = computed(
  () => recycleItems.value.length > 0 && checkedKeys.value.length === recycleItems.value.length,
)

const toggleSelectAll = () => {
  checkedKeys.value = allChecked.value ? [] : recycleItems.value.map(itemKey)
}

// 管理态下点击条目即切换勾选；非管理态条目本身无动作。
const onItemClick = (item: API.WikiRecycleItemVis) => {
  if (manageMode.value) {
    toggleChecked(item)
  }
}

const batchPermanentDelete = () => {
  if (!checkedKeys.value.length) {
    message.warning('请先选择要删除的条目')
    return
  }
  const targets = recycleItems.value.filter((item) => checkedKeys.value.includes(itemKey(item)))
  Modal.confirm({
    title: `永久删除后不可恢复，确认删除选中的 ${targets.length} 项？`,
    async onOk() {
      let success = 0
      let failed = 0
      // 与文档列表批量删除一致：循环调用单条接口，逐条走服务端权限校验，汇总结果。
      for (const item of targets) {
        try {
          const res = await permanentDeleteRecycleItemUsingPost({
            spaceId: item.spaceId,
            itemId: item.itemId,
            itemType: item.itemType,
            confirm: true,
          })
          if (res.data.code === 0) {
            success += 1
          } else {
            failed += 1
          }
        } catch {
          failed += 1
        }
      }
      if (failed === 0) {
        message.success(`已永久删除 ${success} 项`)
      } else {
        message.warning(`已永久删除 ${success} 项，失败 ${failed} 项`)
      }
      await refreshRecycle()
    },
  })
}

// 切换空间与手动刷新必须整体退出管理态：旧勾选属于另一个空间，跨空间残留会造成误删。
const refreshRecycle = async () => {
  resetManageState()
  await fetchRecycleItems()
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

watch(recycleSpaceId, refreshRecycle, { immediate: true })
defineExpose({ refresh: fetchRecycleItems })
</script>
<style scoped>
.recycle-item-managing {
  cursor: pointer;
}

.recycle-item-check {
  /* 对齐列表项主文本的基线，避免复选框浮在行首偏上。 */
  margin-top: 6px;
}

.recycle-manage-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--wiki-border);
  border-radius: 6px;
  background: var(--wiki-panel-head);
  color: var(--wiki-text-muted, #8c8c8c);
}
</style>
