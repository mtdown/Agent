<template>
  <div id="editDocumentWikiPage">
    <h2>编辑文档</h2>
    <a-spin :spinning="fetchLoading">
      <DocumentWikiEditor
        :document-wiki="documentWiki"
        submit-text="保存"
        :loading="saveLoading"
        @submit="handleSubmit"
        @cancel="router.back()"
      />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import DocumentWikiEditor from '@/components/DocumentWikiEditor.vue'
import {
  editDocumentWikiUsingPost,
  getDocumentWikiVisByIdUsingGet,
} from '@/api/documentWikiController.ts'

const route = useRoute()
const router = useRouter()
const documentWiki = ref<API.DocumentWikiVis>({})
const fetchLoading = ref(false)
const saveLoading = ref(false)

const documentWikiId = Number(route.params.id)

const fetchDocumentWiki = async () => {
  fetchLoading.value = true
  try {
    const res = await getDocumentWikiVisByIdUsingGet({ id: documentWikiId })
    if (res.data.code === 0 && res.data.data) {
      documentWiki.value = res.data.data
    } else {
      message.error('获取文档失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取文档失败，' + e.message)
  } finally {
    fetchLoading.value = false
  }
}

const handleSubmit = async (values: API.DocumentWikiEditRequest) => {
  saveLoading.value = true
  try {
    const res = await editDocumentWikiUsingPost({
      ...values,
      id: documentWikiId,
    })
    if (res.data.code === 0) {
      message.success('保存成功')
      router.push(`/documentWiki/${documentWikiId}`)
    } else {
      message.error('保存失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('保存失败，' + e.message)
  } finally {
    saveLoading.value = false
  }
}

onMounted(() => {
  fetchDocumentWiki()
})
</script>

<style scoped>
#editDocumentWikiPage {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 24px 24px;
}
</style>
