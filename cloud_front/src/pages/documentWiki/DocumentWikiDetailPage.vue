<template>
  <div id="documentWikiDetailPage">
    <a-page-header title="文档详情" @back="router.push('/documentWiki')">
      <template #extra>
        <a-button v-if="canEdit" @click="router.push(`/edit_documentWiki/${documentWiki.id}`)">编辑</a-button>
        <a-button v-if="canDelete" danger @click="doDelete">删除</a-button>
      </template>
    </a-page-header>

    <a-spin :spinning="loading">
      <article class="document-body">
        <h1>{{ documentWiki.title }}</h1>
        <a-space wrap class="meta">
          <a-tag v-if="documentWiki.category" color="blue">{{ documentWiki.category }}</a-tag>
          <a-tag v-for="tag in documentWiki.tags" :key="tag">{{ tag }}</a-tag>
          <span>作者：{{ documentWiki.user?.userName ?? documentWiki.userId ?? '-' }}</span>
          <span>编辑于：{{ formatTime(documentWiki.editTime) }}</span>
        </a-space>
        <a-typography-paragraph v-if="documentWiki.summary" type="secondary">
          {{ documentWiki.summary }}
        </a-typography-paragraph>
        <div class="content">{{ documentWiki.content }}</div>
      </article>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  deleteDocumentWikiUsingPost,
  getDocumentWikiVisByIdUsingGet,
} from '@/api/documentWikiController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const loading = ref(false)
const documentWiki = ref<API.DocumentWikiVis>({})

const canEdit = computed(() => {
  const loginUser = loginUserStore.loginUser
  return loginUser?.userRole === 'admin' || loginUser?.id === documentWiki.value.userId
})

const canDelete = canEdit

const fetchDocumentWikiDetail = async () => {
  loading.value = true
  try {
    const res = await getDocumentWikiVisByIdUsingGet({ id: String(props.id) })
    if (res.data.code === 0 && res.data.data) {
      documentWiki.value = res.data.data
    } else {
      message.error('获取文档详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取文档详情失败，' + e.message)
  } finally {
    loading.value = false
  }
}

const doDelete = async () => {
  const id = documentWiki.value.id
  if (!id) {
    return
  }
  const res = await deleteDocumentWikiUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    router.push('/documentWiki')
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
  fetchDocumentWikiDetail()
})
</script>

<style scoped>
#documentWikiDetailPage {
  padding: 0 24px 24px;
}

.document-body {
  max-width: 900px;
  margin: 0 auto;
}

.meta {
  margin-bottom: 16px;
}

.content {
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
