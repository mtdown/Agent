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

    <div v-if="results.length" class="batch-panel">
      <div class="panel-head">
        导入结果（成功 {{ successCount }} / 共 {{ results.length }}）
      </div>
      <a-table
        :columns="resultColumns"
        :data-source="results"
        :pagination="false"
        row-key="input"
        size="small"
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  batchImportFilesUsingPost,
  batchImportUrlsUsingPost,
} from '@/api/documentWikiController.ts'
import { listFolderTreeUsingGet } from '@/api/wikiFolderController.ts'
import { listVisibleSpaceUsingGet } from '@/api/wikiSpaceController.ts'
import { flattenFolderOptions } from './components/wikiShared'

const SUPPORTED_PATTERN = /\.(md|html|htm)$/i

const form = ref<{ spaceId?: string | number; folderId?: string | number }>({})
const spaces = ref<API.WikiSpaceVis[]>([])
const folders = ref<API.WikiFolderVis[]>([])
const urlText = ref('')
const files = ref<File[]>([])
const results = ref<API.BatchImportItemResult[]>([])
const urlLoading = ref(false)
const fileLoading = ref(false)
const fileInputRef = ref<HTMLInputElement>()

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
const resultColumns = [
  { title: '输入', dataIndex: 'input', key: 'input' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '说明', dataIndex: 'message', key: 'message' },
  { title: '文档', dataIndex: 'documentId', key: 'document' },
]

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
</style>
