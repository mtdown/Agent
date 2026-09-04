<template>
  <div id="addDocumentWikiPage">
    <h2>创建文档</h2>
    <DocumentWikiEditor submit-text="创建" :loading="loading" @submit="handleSubmit" @cancel="router.back()" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import DocumentWikiEditor from '@/components/DocumentWikiEditor.vue'
import { addDocumentWikiUsingPost } from '@/api/documentWikiController.ts'

const router = useRouter()
const loading = ref(false)

const handleSubmit = async (values: API.DocumentWikiAddRequest) => {
  loading.value = true
  try {
    const res = await addDocumentWikiUsingPost(values)
    if (res.data.code === 0 && res.data.data) {
      message.success('创建成功')
      router.push(`/documentWiki?open=${res.data.data}`)
    } else {
      message.error('创建失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('创建失败，' + e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
#addDocumentWikiPage {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 24px 24px;
}
</style>
