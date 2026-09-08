<template>
  <article v-if="selectedDocument.id" class="document-preview">
    <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
      <a-space align="center" wrap>
        <a-button type="text" class="back-button" @click="emit('back')">← 返回列表</a-button>
        <h3>{{ selectedDocument.title }}</h3>
      </a-space>
      <a-space wrap>
        <a-button @click="emit('move', selectedDocument)">移动</a-button>
        <a-button
          v-if="isPreviewOnly(selectedDocument)"
          disabled
          title="HTML 原页面文档本阶段仅支持预览，不支持编辑"
        >
          编辑
        </a-button>
        <a-button v-else @click="emit('edit', selectedDocument)">编辑</a-button>
        <a-button danger @click="emit('delete', selectedDocument)">删除</a-button>
      </a-space>
    </a-flex>
    <a-space wrap class="meta">
      <a-tag v-for="tag in selectedDocument.tags" :key="tag">{{ tag }}</a-tag>
      <span>作者：{{ selectedDocument.user?.userName ?? selectedDocument.userId ?? '-' }}</span>
      <span>编辑于：{{ formatTime(selectedDocument.editTime) }}</span>
    </a-space>
    <a-typography-paragraph v-if="selectedDocument.summary" type="secondary">
      {{ selectedDocument.summary }}
    </a-typography-paragraph>
    <DocumentWikiContentViewer
      :content="selectedDocument.content"
      :content-format="selectedDocument.contentFormat"
    />
  </article>

  <template v-else>
    <a-flex
      v-if="!isSearchMode"
      justify="space-between"
      align="center"
      wrap="wrap"
      gap="small"
      class="location-bar"
    >
      <span>
        当前位置：{{ currentSpaceName ?? '-' }}
        <template v-if="currentFolderName"> / {{ currentFolderName }}</template>
        <template v-else> / 根目录</template>
      </span>
      <span class="doc-count">共 {{ browseTotalCount }} 篇文档</span>
    </a-flex>
    <a-list
      v-if="!isSearchMode"
      item-layout="vertical"
      :data-source="browseDocuments"
      :loading="loading"
      :pagination="browsePagination"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta>
            <template #title>
              <button class="result-title" @click="emit('open', item.id)">{{ item.title }}</button>
            </template>
            <template #description>
              <a-space wrap>
                <a-tag v-for="tag in item.tags" :key="tag">{{ tag }}</a-tag>
                <span>编辑于 {{ formatTime(item.editTime) }}</span>
              </a-space>
            </template>
          </a-list-item-meta>
          <div class="summary">{{ item.summary || '暂无摘要' }}</div>
        </a-list-item>
      </template>
      <template #header v-if="!browseDocuments.length && !loading">当前目录暂无文档</template>
    </a-list>

    <a-list
      v-else
      item-layout="vertical"
      :data-source="searchResults"
      :pagination="pagination"
      :loading="loading"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta>
            <template #title>
              <button class="result-title" @click="emit('open', item.id)">{{ item.title }}</button>
            </template>
            <template #description>
              <a-space wrap>
                <a-tag v-for="tag in item.tags" :key="tag">{{ tag }}</a-tag>
                <span>编辑于 {{ formatTime(item.editTime) }}</span>
              </a-space>
            </template>
          </a-list-item-meta>
          <div class="summary">{{ item.summary || '暂无摘要' }}</div>
        </a-list-item>
      </template>
    </a-list>
  </template>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { PaginationProps } from 'ant-design-vue'
import DocumentWikiContentViewer from '@/components/DocumentWikiContentViewer.vue'
import { formatTime, type IdValue } from './wikiShared'

const props = defineProps<{
  selectedDocument: API.DocumentWikiVis
  isSearchMode: boolean
  currentSpaceName?: string
  currentFolderName: string
  browseDocuments: API.DocumentWikiVis[]
  browsePagination?: PaginationProps | false
  searchResults: API.DocumentWikiVis[]
  loading: boolean
  pagination: PaginationProps
}>()
const emit = defineEmits<{
  open: [id: IdValue]
  edit: [document: API.DocumentWikiVis]
  move: [document: API.DocumentWikiVis]
  delete: [document: API.DocumentWikiVis]
  back: []
}>()

// Uploaded HTML original-page documents are preview-only in this stage.
const isPreviewOnly = (documentWiki?: API.DocumentWikiVis) =>
  documentWiki?.contentFormat === 'html'

// Total document count for the location bar: paged browse modes read it from the pagination
// total, inline folder browsing falls back to the loaded rows.
const browseTotalCount = computed(() => {
  if (props.browsePagination && typeof props.browsePagination === 'object') {
    return props.browsePagination.total ?? props.browseDocuments.length
  }
  return props.browseDocuments.length
})
</script>
<style scoped>
.location-bar {
  margin-bottom: 12px;
  color: #595959;
}
.doc-count {
  color: #999;
}
.back-button {
  padding: 0 4px;
  color: var(--wiki-text-muted);
}
.back-button:hover {
  color: #a14f16;
}
.result-title {
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  padding: 0;
}
.result-title:hover {
  color: #1677ff;
}
.document-preview {
  padding-top: 4px;
}
.meta {
  margin-bottom: 14px;
}
.summary {
  color: #666;
  white-space: pre-wrap;
}
</style>
