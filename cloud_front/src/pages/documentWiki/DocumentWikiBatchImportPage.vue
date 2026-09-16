<template>
  <div id="documentWikiBatchImportPage">
    <div class="batch-panel">
      <div class="panel-head">导入目标</div>
      <a-space wrap size="middle" class="target-row">
        <a-select
          v-model:value="form.spaceId"
          placeholder="目标文档空间"
          style="min-width: 220px"
          :options="spaceOptions"
          @change="onSpaceChange"
        />
        <a-select
          v-model:value="form.folderId"
          placeholder="目标文件夹（可选，默认空间根目录）"
          style="min-width: 260px"
          allow-clear
          :options="folderOptions"
        />
      </a-space>
    </div>

    <div class="batch-panel">
      <div class="panel-head">网页地址导入</div>
      <a-textarea
        v-model:value="urlText"
        :rows="6"
        placeholder="每行填写一个网页地址，仅支持 http / https"
      />
      <div class="panel-actions">
        <a-button type="primary" :loading="urlLoading" @click="submitUrls">导入网页</a-button>
        <a-button :disabled="!urlText.trim()" @click="urlText = ''">清空</a-button>
      </div>
    </div>

    <div class="batch-panel">
      <div class="panel-head">本地文件导入</div>
      <a-space wrap>
        <a-button @click="openFilePicker">选择文件</a-button>
        <span class="file-hint">支持 md、html、htm，可多选</span>
        <input
          ref="fileInputRef"
          class="file-input"
          type="file"
          multiple
          accept=".md,.html,.htm"
          @change="onFileChange"
        />
      </a-space>
      <ul v-if="files.length" class="file-list">
        <li v-for="file in files" :key="file.name">{{ file.name }}</li>
      </ul>
      <div class="panel-actions">
        <a-button type="primary" :loading="fileLoading" @click="submitFiles">上传文件</a-button>
        <a-button v-if="files.length" @click="clearFiles">移除文件</a-button>
      </div>
    </div>

    <div class="batch-panel">
      <div class="panel-head">JSON 语料导入</div>
      <a-space wrap>
        <a-button :disabled="jsonLoading" @click="openJsonPicker">选择 JSON 文件</a-button>
        <span class="file-hint">单个 .json 文件，每个条目生成一篇文档，条目数不限，最大 30MB</span>
        <input
          ref="jsonInputRef"
          class="file-input"
          type="file"
          accept=".json"
          @change="onJsonChange"
        />
      </a-space>
      <ul v-if="jsonFile" class="file-list">
        <li>{{ jsonFile.name }}（{{ jsonFileSizeText }}）</li>
      </ul>
      <div class="panel-actions">
        <a-button
          type="primary"
          :loading="jsonLoading"
          :disabled="jsonLoading || !jsonFile"
          @click="submitJson"
        >
          导入 JSON
        </a-button>
        <a-button v-if="jsonFile" :disabled="jsonLoading" @click="clearJsonFile">移除文件</a-button>
      </div>
      <a-alert
        v-if="jsonIndexNotice"
        class="json-notice"
        type="info"
        show-icon
        message="切片与向量化在后台异步执行"
        :description="jsonIndexNotice"
      />
    </div>

    <div v-if="results.length" class="batch-panel">
      <div class="panel-head result-head">
        <span>导入结果（成功 {{ successCount }} / 共 {{ results.length }}）</span>
        <a-checkbox :checked="failedOnly" @change="onFailedOnlyChange">仅看失败</a-checkbox>
      </div>
      <a-table
        :columns="resultColumns"
        :data-source="visibleResults"
        :pagination="resultPagination"
        row-key="input"
        size="small"
        @change="onResultTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'SUCCESS' ? 'orange' : 'red'">
              {{ record.status === 'SUCCESS' ? '成功' : '失败' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'document'">
            <router-link v-if="record.documentId" :to="`/documentWiki/${record.documentId}`">
              查看文档
            </router-link>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
      <p v-if="results.length && !visibleResults.length" class="file-hint empty-hint">
        没有失败条目，全部 {{ results.length }} 条均导入成功。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  batchImportFilesUsingPost,
  batchImportJsonUsingPost,
  batchImportUrlsUsingPost,
} from '@/api/documentWikiController.ts'
import { listFolderTreeUsingGet } from '@/api/wikiFolderController.ts'
import { listVisibleSpaceUsingGet } from '@/api/wikiSpaceController.ts'
import { flattenFolderOptions } from './components/wikiShared'

const SUPPORTED_PATTERN = /\.(md|html|htm)$/i

const JSON_PATTERN = /\.json$/i

/** Kept in step with the backend JSON import limit, which itself matches multipart max-file-size. */
const MAX_JSON_SIZE = 30 * 1024 * 1024

const RESULT_PAGE_SIZE = 20

const form = ref<{ spaceId?: string | number; folderId?: string | number }>({})
const spaces = ref<API.WikiSpaceVis[]>([])
const folders = ref<API.WikiFolderVis[]>([])
const urlText = ref('')
const files = ref<File[]>([])
const results = ref<API.BatchImportItemResult[]>([])
const urlLoading = ref(false)
const fileLoading = ref(false)
const fileInputRef = ref<HTMLInputElement>()
const jsonFile = ref<File>()
const jsonLoading = ref(false)
const jsonInputRef = ref<HTMLInputElement>()
const jsonIndexNotice = ref('')
const failedOnly = ref(false)
const resultPage = ref(1)

const spaceOptions = computed(() =>
  spaces.value.map((space) => ({ label: space.name ?? '未命名空间', value: space.id })),
)
const folderOptions = computed(() => [
  { label: '空间根目录', value: '' },
  ...flattenFolderOptions(folders.value),
])
const successCount = computed(
  () => results.value.filter((item) => item.status === 'SUCCESS').length,
)
const jsonFileSizeText = computed(() => formatSize(jsonFile.value?.size ?? 0))
const visibleResults = computed(() =>
  failedOnly.value ? results.value.filter((item) => item.status !== 'SUCCESS') : results.value,
)
const resultPagination = computed(() => ({
  current: resultPage.value,
  pageSize: RESULT_PAGE_SIZE,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100'],
  showTotal: (total: number) => `共 ${total} 条`,
}))
const resultColumns = [
  { title: '输入', dataIndex: 'input', key: 'input' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '说明', dataIndex: 'message', key: 'message' },
  { title: '文档', dataIndex: 'documentId', key: 'document' },
]

watch(results, () => {
  resultPage.value = 1
})

const formatSize = (bytes: number) => {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}

const fetchSpaces = async () => {
  const res = await listVisibleSpaceUsingGet()
  if (res.data.code === 0) {
    spaces.value = res.data.data ?? []
    if (!form.value.spaceId && spaces.value[0]?.id) {
      form.value.spaceId = spaces.value[0].id
      await loadFolders(form.value.spaceId)
    }
  } else {
    message.error('获取空间列表失败，' + res.data.message)
  }
}

const loadFolders = async (spaceId?: string | number) => {
  form.value.folderId = undefined
  if (!spaceId) {
    folders.value = []
    return
  }
  const res = await listFolderTreeUsingGet({ spaceId })
  if (res.data.code === 0) {
    folders.value = res.data.data ?? []
  }
}

const onSpaceChange = (spaceId?: string | number) => loadFolders(spaceId)

const requireSpace = () => {
  if (!form.value.spaceId) {
    message.warning('请先选择目标文档空间')
    return false
  }
  return true
}

const submitUrls = async () => {
  const urls = urlText.value
    .split(/[\r\n]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
  if (urls.length === 0) {
    message.warning('请至少填写一个网页地址')
    return
  }
  if (!requireSpace()) return
  urlLoading.value = true
  try {
    const res = await batchImportUrlsUsingPost({
      spaceId: form.value.spaceId,
      folderId: form.value.folderId || undefined,
      urls,
    })
    if (res.data.code === 0) {
      results.value = res.data.data ?? []
      message.success(`导入完成，成功 ${successCount.value} / 共 ${results.value.length}`)
    } else {
      message.error('导入失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('导入失败，' + e.message)
  } finally {
    urlLoading.value = false
  }
}

const openFilePicker = () => fileInputRef.value?.click()

const onFileChange = (event: Event) => {
  const picked = Array.from((event.target as HTMLInputElement).files ?? [])
  const invalid = picked.filter((file) => !SUPPORTED_PATTERN.test(file.name))
  if (invalid.length > 0) {
    message.error(`已忽略不支持的文件：${invalid.map((file) => file.name).join('、')}`)
  }
  const empty = picked.filter((file) => SUPPORTED_PATTERN.test(file.name) && file.size === 0)
  if (empty.length > 0) {
    message.error(`已忽略空文件：${empty.map((file) => file.name).join('、')}`)
  }
  files.value = picked.filter(
    (file) => SUPPORTED_PATTERN.test(file.name) && file.size > 0,
  )
}

const clearFiles = () => {
  files.value = []
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

const submitFiles = async () => {
  if (files.value.length === 0) {
    message.warning('请先选择要上传的文件')
    return
  }
  if (!requireSpace()) return
  fileLoading.value = true
  try {
    const res = await batchImportFilesUsingPost(
      {
        spaceId: form.value.spaceId as string | number,
        folderId: form.value.folderId || undefined,
      },
      files.value,
    )
    if (res.data.code === 0) {
      results.value = res.data.data ?? []
      message.success(`上传完成，成功 ${successCount.value} / 共 ${results.value.length}`)
      clearFiles()
    } else {
      message.error('上传失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('上传失败，' + e.message)
  } finally {
    fileLoading.value = false
  }
}

const openJsonPicker = () => jsonInputRef.value?.click()

const onJsonChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const picked = input.files?.[0]
  jsonIndexNotice.value = ''
  const reject = (reason: string) => {
    message.error(reason)
    jsonFile.value = undefined
    input.value = ''
  }
  if (!picked) {
    jsonFile.value = undefined
    return
  }
  if (!JSON_PATTERN.test(picked.name)) {
    reject('JSON 导入仅支持 .json 文件')
    return
  }
  if (picked.size === 0) {
    reject('不能导入空文件')
    return
  }
  if (picked.size > MAX_JSON_SIZE) {
    reject(`文件不能超过 30MB，当前 ${formatSize(picked.size)}`)
    return
  }
  jsonFile.value = picked
}

const clearJsonFile = () => {
  jsonFile.value = undefined
  if (jsonInputRef.value) {
    jsonInputRef.value.value = ''
  }
}

const submitJson = async () => {
  if (!jsonFile.value) {
    message.warning('请先选择要导入的 JSON 文件')
    return
  }
  if (!requireSpace()) return
  jsonIndexNotice.value = ''
  jsonLoading.value = true
  const startedAt = Date.now()
  try {
    const res = await batchImportJsonUsingPost(
      {
        spaceId: form.value.spaceId as string | number,
        folderId: form.value.folderId || undefined,
      },
      jsonFile.value,
    )
    if (res.data.code === 0) {
      results.value = res.data.data ?? []
      const seconds = ((Date.now() - startedAt) / 1000).toFixed(1)
      message.success(
        `导入完成，成功 ${successCount.value} / 共 ${results.value.length}，耗时 ${seconds}s`,
      )
      // chunking is triggered AFTER_COMMIT and runs off the request path, so the chunks do not
      // exist yet when this response arrives — the operator must wait before starting a test
      jsonIndexNotice.value =
        `接口返回时切片尚未生成。请稍候到该空间的文档列表确认文档数达到 ${results.value.length}，` +
        '确认后再开始检索测试；文档数不足说明仍有条目在异步索引中。'
      clearJsonFile()
    } else {
      message.error('导入失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('导入失败，' + e.message)
  } finally {
    jsonLoading.value = false
  }
}

const onFailedOnlyChange = (event: any) => {
  failedOnly.value = Boolean(event?.target?.checked)
  resultPage.value = 1
}

const onResultTableChange = (pagination: { current?: number }) => {
  resultPage.value = pagination?.current ?? 1
}

onMounted(fetchSpaces)
</script>

<style scoped>
#documentWikiBatchImportPage {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px 24px 32px;
  background: var(--wiki-bg);
  color: var(--wiki-text);
  min-height: 100%;
}

.batch-panel {
  margin-bottom: 16px;
  padding: 16px;
  background: var(--wiki-panel);
  border: 1px solid var(--wiki-border);
  border-radius: 8px;
}

.panel-head {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--wiki-text);
}

.target-row {
  width: 100%;
}

.panel-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.file-input {
  display: none;
}

.file-hint {
  color: var(--wiki-text-muted);
}

.file-list {
  margin: 12px 0 0;
  padding-left: 18px;
  color: var(--wiki-text-muted);
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.json-notice {
  margin-top: 12px;
}

.empty-hint {
  margin: 12px 0 0;
}
</style>
