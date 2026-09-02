<template>
  <div id="documentWikiListPage">
    <a-flex justify="space-between" align="center" wrap="wrap" gap="middle" class="page-header">
      <h2>Wiki 文档</h2>
      <a-button type="primary" @click="router.push('/add_documentWiki')">创建文档</a-button>
    </a-flex>

    <a-form layout="inline" :model="searchParams" class="search-form" @finish="doSearch">
      <a-form-item label="关键词" name="searchText">
        <a-input v-model:value="searchParams.searchText" placeholder="搜索标题、摘要、正文" allow-clear />
      </a-form-item>
      <a-form-item label="分类" name="category">
        <a-input v-model:value="searchParams.category" placeholder="请输入分类" allow-clear />
      </a-form-item>
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="searchParams.tags"
          mode="tags"
          placeholder="请输入标签"
          style="min-width: 180px"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>

    <a-list
      item-layout="vertical"
      :data-source="dataList"
      :pagination="pagination"
      :loading="loading"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <template #actions>
            <a-button type="link" @click="router.push(`/documentWiki/${item.id}`)">查看</a-button>
            <a-button type="link" @click="router.push(`/edit_documentWiki/${item.id}`)">编辑</a-button>
            <a-button type="link" danger @click="doDelete(item.id)">删除</a-button>
          </template>
          <a-list-item-meta>
            <template #title>
              <router-link :to="`/documentWiki/${item.id}`">{{ item.title }}</router-link>
            </template>
            <template #description>
              <a-space wrap>
                <a-tag v-if="item.category" color="blue">{{ item.category }}</a-tag>
                <a-tag v-for="tag in item.tags" :key="tag">{{ tag }}</a-tag>
                <span>编辑于 {{ formatTime(item.editTime) }}</span>
              </a-space>
            </template>
          </a-list-item-meta>
          <div class="summary">{{ item.summary || '暂无摘要' }}</div>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  deleteDocumentWikiUsingPost,
  listDocumentWikiVisByPageWithCacheUsingPost,
} from '@/api/documentWikiController.ts'

const router = useRouter()
const dataList = ref<API.DocumentWikiVis[]>([])
const total = ref(0)
const loading = ref(false)

const searchParams = reactive<API.DocumentWikiQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'editTime',
  sortOrder: 'descend',
})

const pagination = computed(() => {
  return {
    current: searchParams.current ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showTotal: (total: number) => `共 ${total} 条`,
    onChange: (page: number, pageSize: number) => {
      searchParams.current = page
      searchParams.pageSize = pageSize
      fetchData()
    },
  }
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listDocumentWikiVisByPageWithCacheUsingPost({ ...searchParams })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = Number(res.data.data.total ?? 0)
    } else {
      message.error('获取文档失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取文档失败，' + e.message)
  } finally {
    loading.value = false
  }
}

const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const doDelete = async (id?: number) => {
  if (!id) {
    return
  }
  const res = await deleteDocumentWikiUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    fetchData()
  } else {
    message.error('删除失败，' + res.data.message)
  }
}

const formatTime = (time?: string) => {
  if (!time) {
    return '-'
  }
  const date = /^\d+$/.test(time) ? new Date(Number(time)) : new Date(time)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#documentWikiListPage {
  padding: 0 24px 24px;
}

.page-header {
  margin-bottom: 16px;
}

.search-form {
  margin-bottom: 16px;
}

.summary {
  color: #666;
  white-space: pre-wrap;
}
</style>
