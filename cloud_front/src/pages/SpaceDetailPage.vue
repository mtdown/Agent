<template>
  <div id="spaceDetailPage">
    <!-- 空间信息 -->
    <a-flex justify="space-between">
      <h2>{{ space.spaceName }}（私有空间）</h2>
      <a-space size="middle">
        <a-button type="primary" :href="`/add_picture?spaceId=${id}`" target="_blank">
          + 创建图片
        </a-button>
        <a-tooltip
          :title="`占用空间 ${formatSize(space.totalSize)} / ${formatSize(space.maxSize)}`"
        >
          <a-progress
            type="circle"
            :percent="((space.totalSize * 100) / space.maxSize).toFixed(1)"
            :size="42"
          />
        </a-tooltip>
      </a-space>
    </a-flex>
    <!-- 图片列表 -->
    <PictureList :dataList="dataList" :loading="loading" :showOp="true" />
    <a-pagination
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      @change="onPageChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { deleteSpaceUsingPost, getSpaceVisByIdUsingGet } from '@/api/spaceController.ts'
import PictureList from '@/components/PictureList.vue'
import { message } from 'ant-design-vue'
import { listPictureByPageUsingPost } from '@/api/pictureController.ts'
import { formatSize } from '@/utils'

const props = defineProps<{
  id: string | number
}>()
const space = ref<API.SpaceVis>({})

const fetchSpaceDetail = async () => {
  try {
    const res = await getSpaceVisByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    } else {
      message.error('获取空间详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取空间详情失败：' + e.message)
  }
}

onMounted(() => {
  fetchSpaceDetail()
})

const dataList = ref([])
const total = ref(0)
const loading = ref(true)

const searchParams = ref<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const onPageChange = (page: number, pageSize: number) => {
  searchParams.value.current = page
  searchParams.value.pageSize = pageSize
  fetchData()
}

const fetchData = async () => {
  loading.value = true

  const params = {
    spaceId: props.id,
    ...searchParams.value,
  }
  const res = await listPictureByPageUsingPost(params)
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#spaceDetailPage {
  margin-bottom: 16px;
}
:deep(.preview-image) {
  width: 500px; /* 固定宽度为 500px */
  height: 500px; /* 固定高度为 500px */
  object-fit: contain; /* 关键！保持宽高比，在500x500的框内完整显示 */
}
.image-preview-card :deep(.ant-card-body) {
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center; /* 垂直居中 */
}
</style>
