<template>
  <article v-if="selectedDocument.id" class="document-preview">
    <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
      <h3>{{ selectedDocument.title }}</h3>
      <a-space wrap>
        <a-button @click="emit('move', selectedDocument)">移动</a-button>
        <a-button @click="router.push(`/documentWiki/${selectedDocument.id}`)">查看</a-button>
        <a-button @click="router.push(`/edit_documentWiki/${selectedDocument.id}`)">编辑</a-button>
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
    <div class="content">
      <template v-if="contentBlocks.length">
        <template v-for="block in contentBlocks" :key="block.key">
          <component
            :is="block.tag"
            v-if="block.type === 'heading'"
            :id="block.id"
            class="document-heading"
          >
            {{ block.text }}
          </component>
          <p v-else class="document-paragraph">{{ block.text }}</p>
        </template>
      </template>
      <template v-else>{{ selectedDocument.content }}</template>
    </div>
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
      <span class="doc-count">共 {{ browseDocuments.length }} 篇文档</span>
    </a-flex>
    <a-list
      v-if="!isSearchMode"
      item-layout="vertical"
      :data-source="browseDocuments"
      :loading="loading"
      :pagination="false"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <template #actions>
            <a-button type="link" @click="emit('open', item.id)">打开</a-button>
            <a-button type="link" @click="router.push(`/documentWiki/${item.id}`)">查看</a-button>
            <a-button type="link" @click="router.push(`/edit_documentWiki/${item.id}`)"
              >编辑</a-button
            >
            <a-button type="link" @click="emit('move', item)">移动</a-button>
            <a-button type="link" danger @click="emit('delete', item)">删除</a-button>
          </template>
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
          <template #actions>
            <a-button type="link" @click="emit('open', item.id)">打开</a-button>
            <a-button type="link" @click="router.push(`/documentWiki/${item.id}`)">查看</a-button>
            <a-button type="link" @click="router.push(`/edit_documentWiki/${item.id}`)"
              >编辑</a-button
            >
            <a-button type="link" @click="emit('move', item)">移动</a-button>
            <a-button type="link" danger @click="emit('delete', item)">删除</a-button>
          </template>
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
import { useRouter } from 'vue-router'
import type { PaginationProps } from 'ant-design-vue'
import { formatTime, type IdValue } from './wikiShared'

const router = useRouter()
const props = defineProps<{
  selectedDocument: API.DocumentWikiVis
  isSearchMode: boolean
  currentSpaceName?: string
  currentFolderName: string
  browseDocuments: API.DocumentWikiVis[]
  searchResults: API.DocumentWikiVis[]
  loading: boolean
  pagination: PaginationProps
}>()
const emit = defineEmits<{
  open: [id: IdValue]
  move: [document: API.DocumentWikiVis]
  delete: [document: API.DocumentWikiVis]
}>()

const contentBlocks = computed(() => {
  const content = props.selectedDocument.content ?? ''
  const lines = content.split(/\r?\n/)
  const headingIndexes = new Map<number, string>()
  let headingIndex = 0
  return lines
    .map((line, index) => {
      const heading = /^(#{1,4})\s+(.+)$/.exec(line)
      if (heading) {
        const id = `wiki-heading-${headingIndex}`
        headingIndexes.set(index, id)
        headingIndex += 1
        const level = Math.min(heading[1].length + 1, 4)
        return {
          key: `${index}-${id}`,
          type: 'heading',
          tag: `h${level}`,
          id,
          text: heading[2].trim(),
        }
      }
      return {
        key: `${index}-p`,
        type: 'paragraph',
        tag: 'p',
        id: undefined,
        text: line.trim(),
      }
    })
    .filter((block) => block.text)
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
.content {
  line-height: 1.8;
  white-space: pre-wrap;
}

.document-heading {
  scroll-margin-top: 18px;
  margin: 22px 0 10px;
  color: #251f18;
  font-weight: 600;
}

.document-paragraph {
  margin: 0 0 12px;
  color: #3f3429;
  line-height: 1.8;
}
</style>
