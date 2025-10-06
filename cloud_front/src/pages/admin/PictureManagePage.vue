<template>
  <div id="pictureManagePage">
    <a-flex justify="space-between">
      <h2>图片管理</h2>
      <a-button type="primary" href="/add_picture" target="_blank">+ 创建</a-button>
    </a-flex>
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词" name="searchText">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="类型" name="category">
        <a-input v-model:value="searchParams.category" placeholder="请输入类型" />
      </a-form-item>
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="searchParams.tags"
          mode="tags"
          placeholder="请输入标签"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-table
      :columns="columns"
      :data-source="dataList"
      :pagination="pagination"
      @change="doTableChange"
      :scroll="{ x: 'max-content' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'url'">
          <a-image :src="record.url" :width="200" />
        </template>
        <template v-if="column.dataIndex === 'tags'">
          <a-space wrap>
            <a-tag v-for="tag in JSON.parse(record.tags || '[]')" :key="tag">
              {{ tag }}
            </a-tag>
          </a-space>
        </template>
        <template v-if="column.dataIndex === 'picInfo'">
          <div>格式: {{ record.picFormat }}</div>
          <div>宽度: {{ record.picWidth }}</div>
          <div>高度: {{ record.picHeight }}</div>
          <div>宽高比: {{ record.picScale }}</div>
          <div>大小: {{ (record.picSize / 1024).toFixed(2) }} KB</div>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.dataIndex === 'editTime'">
          {{ dayjs(record.editTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" :href="`/add_picture?id=${record.id}`" target="_blank">
              编辑
            </a-button>
            <a-button type="link" danger @click="doDelete(record.id)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { deletePictureUsingPost, listPictureByPageUsingPost } from '@/api/pictureController'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import type { API } from '@/api/typings'

// 表格列定义
const columns = [
  { title: 'id', dataIndex: 'id', width: 80 },
  { title: '图片', dataIndex: 'url' },
  { title: '名称', dataIndex: 'name' },
  { title: '简介', dataIndex: 'introduction', ellipsis: true },
  { title: '类型', dataIndex: 'category' },
  { title: '标签', dataIndex: 'tags' },
  { title: '图片信息', dataIndex: 'picInfo' },
  { title: '用户id', dataIndex: 'userId', width: 80 },
  { title: '创建时间', dataIndex: 'createTime' },
  { title: '编辑时间', dataIndex: 'editTime' },
  { title: '操作', key: 'action' },
]

const dataList = ref([])
const total = ref(0)
// 搜索参数，包含分页和排序
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 分页配置
const pagination = computed(() => {
  return {
    current: searchParams.current ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 获取数据
const fetchData = async () => {
  try {
    const res = await listPictureByPageUsingPost({ ...searchParams })
    if (res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('获取数据失败，' + error.message)
  }
}

// 删除操作
const doDelete = async (id: number) => {
  try {
    const res = await deletePictureUsingPost({ id })
    if (res.data.data) {
      message.success('删除成功')
      fetchData() // 重新加载数据
    } else {
      message.error('删除失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('删除失败，' + error.message)
  }
}

// 搜索
const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

// 表格变化（分页、排序）
const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 页面加载时获取数据
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#pictureManagePage {
  max-height: 3px;
}
</style>
