<template>
  <article v-if="selectedDocument.id" class="document-preview">
    <div class="preview-toolbar">
      <a-button class="back-button" @click="emit('back')">返回列表</a-button>
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
    </div>
    <h1 class="article-title">{{ selectedDocument.title }}</h1>
    <div class="article-metadata">
      <span v-if="publicationTime">时间：{{ publicationTime }}</span>
      <span v-if="sourceSite">
        来源：
        <a
          v-if="sourceHref"
          :href="sourceHref"
          target="_blank"
          rel="noopener noreferrer"
        >{{ sourceSite }}</a>
        <span v-else>{{ sourceSite }}</span>
      </span>
      <span v-if="articleMetadata.originalAuthor">原文作者：{{ articleMetadata.originalAuthor }}</span>
      <span>创建人：{{ selectedDocument.user?.userName ?? selectedDocument.userId ?? '-' }}</span>
    </div>
    <DocumentWikiContentViewer
      :content="displayContent"
      :content-format="selectedDocument.contentFormat"
    />
  </article>

  <template v-else>
    <div class="browse-list-body">
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
        <a-space size="small" align="center">
          <span class="doc-count">共 {{ browseTotalCount }} 篇文档</span>
          <a-input-number
            v-model:value="pageInput"
            :min="1"
            :max="maxPage"
            :disabled="!maxPage"
            size="small"
            placeholder="页码"
            style="width: 90px"
            :controls="false"
            @press-enter="confirmJump"
          />
          <a-button size="small" :disabled="!pageInput" @click="confirmJump">跳转</a-button>
        </a-space>
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
    </div>
  </template>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
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
  jumpToPage: [page: number]
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

// 跳转到指定页：输入受 a-input-number 的 min/max 约束（max = ceil(total/pageSize)），
// 回车或点按钮触发；清空输入框避免连续跳转时残留。
const pageInput = ref<number>()
const maxPage = computed(() => {
  if (!props.browsePagination || typeof props.browsePagination !== 'object') return undefined
  const total = Number(props.browsePagination.total ?? 0)
  const size = Number(props.browsePagination.pageSize ?? 0)
  if (!total || !size) return undefined
  return Math.ceil(total / size)
})
const confirmJump = () => {
  const target = Number(pageInput.value)
  if (!Number.isFinite(target) || target < 1) return
  emit('jumpToPage', target)
  pageInput.value = undefined
}

interface ArticleMetadata {
  publishedAt?: string
  sourceSite?: string
  originalAuthor?: string
}

const metadataText = (value: unknown) =>
  typeof value === 'string' && value.trim() ? value.trim() : undefined

interface ParsedLegacyMetadata {
  publishedAt?: string
  sourceSite?: string
  sourceUrl?: string
  originalAuthor?: string
  body: string
}

const parseLegacyArticleMetadata = (content?: string): ParsedLegacyMetadata => {
  const raw = content ?? ''
  const lines = raw.split(/\r?\n/)
  const values: Record<string, string> = {}
  let consumed = 0
  let foundMetadata = false
  for (const line of lines) {
    const normalized = line.trim().replace(/^---\s*/, '').replace(/\s*---$/, '')
    if (!normalized) {
      if (foundMetadata) consumed += 1
      else if (consumed === 0) consumed += 1
      continue
    }
    const match = normalized.match(/^(?:\*\*)?([\w-]+)(?:\*\*)?\s*:\s*(.*)$/)
    if (!match) break
    const key = match[1].toLowerCase()
    if (!['title', 'column', 'channel', 'pubdate', 'sourceurl', 'metadataid', 'bodyshort', 'author', 'originalauthor', 'source', 'sourcesite'].includes(key)) break
    foundMetadata = true
    consumed += 1
    let value = match[2].replace(/\*\*/g, '').trim()
    const markdownLink = value.match(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/i)
    if (markdownLink) {
      const linkLabel = markdownLink[1].trim()
      if (/^https?:\/\//i.test(linkLabel)) {
        try { values.sourceSite ??= new URL(markdownLink[2]).hostname.replace(/^www\./i, '') } catch { /* keep optional */ }
      } else {
        values.sourceSite ??= linkLabel
      }
      values.sourceUrl ??= markdownLink[2].trim()
      value = markdownLink[2].trim()
    }
    values[key] = value.replace(/^['"]|['"]$/g, '')
  }
  if (!foundMetadata) return { body: raw }
  while (lines[consumed]?.trim() === '' || /^\s*(?:---|___|\*\*\*)\s*$/.test(lines[consumed] ?? '')) consumed += 1
  const sourceUrl = values.sourceurl ?? values.sourceUrl
  if (sourceUrl && !values.sourceSite) {
    try { values.sourceSite = new URL(sourceUrl).hostname.replace(/^www\./i, '') } catch { /* ignore invalid old metadata */ }
  }
  return {
    publishedAt: values.pubdate,
    sourceSite: values.sourcesite ?? values.source ?? values.sourceSite,
    sourceUrl: sourceUrl,
    originalAuthor: values.originalauthor ?? values.author,
    body: lines.slice(consumed).join('\n'),
  }
}

const legacyMetadata = computed(() => parseLegacyArticleMetadata(props.selectedDocument.content))
const displayContent = computed(() => legacyMetadata.value.body)

const articleMetadata = computed<ArticleMetadata>(() => {
  try {
    const parsed = JSON.parse(props.selectedDocument.metadataJson ?? '{}')
    return {
      publishedAt: metadataText(parsed.publishedAt) ?? legacyMetadata.value.publishedAt,
      sourceSite: metadataText(parsed.sourceSite) ?? legacyMetadata.value.sourceSite,
      originalAuthor: metadataText(parsed.originalAuthor) ?? legacyMetadata.value.originalAuthor,
    }
  } catch {
    return {
      publishedAt: legacyMetadata.value.publishedAt,
      sourceSite: legacyMetadata.value.sourceSite,
      originalAuthor: legacyMetadata.value.originalAuthor,
    }
  }
})

const sourceHref = computed(() => {
  const candidate = (props.selectedDocument.sourceUrl ?? legacyMetadata.value.sourceUrl)?.trim()
  if (!candidate) return undefined
  try {
    const parsed = new URL(candidate)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : undefined
  } catch {
    return undefined
  }
})

const sourceSite = computed(() => {
  if (articleMetadata.value.sourceSite) return articleMetadata.value.sourceSite
  if (!sourceHref.value) return undefined
  return new URL(sourceHref.value).hostname.replace(/^www\./i, '')
})

const publicationTime = computed(() => {
  const raw = articleMetadata.value.publishedAt
  if (!raw) return undefined
  const formatted = formatTime(raw)
  return formatted === '-' ? raw : formatted
})
</script>
<style scoped>
/* 列表主体：占满父级剩余高度；位置栏固定在顶部，a-list 的数据区在栏内滚动，
   分页器（.ant-list-pagination 是 .ant-list 直接子元素）常驻底部。 */
.browse-list-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.browse-list-body :deep(.ant-list) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.browse-list-body :deep(.ant-list .ant-spin-nested-loading) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-width: thin;
  /* 默认透明、仅在悬停时显形，避免内容"略长一点"也常驻一根灰条。 */
  scrollbar-color: transparent transparent;
  transition: scrollbar-color 0.2s ease;
  padding-right: 4px;
}

.browse-list-body :deep(.ant-list .ant-spin-nested-loading:hover) {
  scrollbar-color: var(--wiki-accent) var(--wiki-muted);
}

.browse-list-body :deep(.ant-list .ant-spin-nested-loading::-webkit-scrollbar) {
  width: 8px;
  background: transparent;
}

.browse-list-body :deep(.ant-list .ant-spin-nested-loading::-webkit-scrollbar-thumb) {
  background: transparent;
  border-radius: 999px;
  transition: background 0.2s ease;
}

.browse-list-body :deep(.ant-list .ant-spin-nested-loading:hover::-webkit-scrollbar-thumb) {
  background: var(--wiki-accent);
}

.location-bar {
  margin-bottom: 12px;
  color: #595959;
  flex-shrink: 0;
}
.doc-count {
  color: #999;
}
.back-button {
  flex-shrink: 0;
}
.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.article-title {
  margin: 22px 0 16px;
  color: var(--wiki-text);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.4;
  text-align: center;
}
.article-metadata {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin: 0 0 22px;
  color: var(--wiki-text-muted);
  text-align: center;
}
.article-metadata a {
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
.summary {
  color: #666;
  white-space: pre-wrap;
}
</style>
