<template>
  <a-form layout="vertical" :model="formState" @finish="handleFinish">
    <a-form-item label="标题" name="title" :rules="[{ required: true, message: '请输入标题' }]">
      <a-input v-model:value="formState.title" placeholder="请输入文档标题" allow-clear />
    </a-form-item>

    <a-form-item label="位置" required>
      <a-space wrap>
        <a-select
          v-model:value="formState.spaceId"
          placeholder="选择文档空间"
          style="min-width: 220px"
          :options="spaceOptions"
          @change="handleSpaceChange"
        />
        <a-select
          v-model:value="formState.folderId"
          placeholder="选择文件夹"
          style="min-width: 220px"
          :options="folderOptions"
          :loading="folderLoading"
          allow-clear
        />
      </a-space>
    </a-form-item>

    <a-form-item label="标签" name="tags">
      <a-select v-model:value="formState.tags" mode="tags" placeholder="请输入标签" allow-clear />
    </a-form-item>
    <a-form-item label="摘要" name="summary">
      <a-textarea
        v-model:value="formState.summary"
        placeholder="不填写时会自动截取正文前半部分"
        :rows="3"
        allow-clear
      />
    </a-form-item>
    <a-form-item label="正文" name="content" :rules="[{ required: true, message: '请输入正文' }]">
      <a-textarea
        v-model:value="formState.content"
        placeholder="请输入 Wiki 文档正文"
        :rows="18"
        allow-clear
      />
    </a-form-item>
    <a-form-item>
      <a-space>
        <a-button type="primary" html-type="submit" :loading="loading">{{ submitText }}</a-button>
        <a-button @click="emit('cancel')">取消</a-button>
      </a-space>
    </a-form-item>
  </a-form>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { listVisibleSpaceUsingGet } from '@/api/wikiSpaceController.ts'
import { listFolderTreeUsingGet } from '@/api/wikiFolderController.ts'

type SelectValue = string | number | undefined

const props = withDefaults(
  defineProps<{
    documentWiki?: API.DocumentWikiVis
    submitText?: string
    loading?: boolean
  }>(),
  {
    submitText: '保存',
    loading: false,
  },
)

const emit = defineEmits<{
  submit: [values: API.DocumentWikiEditRequest]
  cancel: []
}>()

const spaces = ref<API.WikiSpaceVis[]>([])
const folders = ref<API.WikiFolderVis[]>([])
const folderLoading = ref(false)

const formState = reactive<API.DocumentWikiEditRequest>({
  title: '',
  content: '',
  summary: '',
  tags: [],
  spaceId: undefined,
  folderId: undefined,
})

const spaceOptions = computed(() =>
  spaces.value.map((space) => ({
    label: `${spaceRegionName(space.type)} / ${space.name ?? space.id}`,
    value: space.id,
  })),
)

const folderOptions = computed(() => [
  { label: '空间根目录', value: '' },
  ...flattenFolders(folders.value),
])

watch(
  () => props.documentWiki,
  (documentWiki) => {
    formState.id = documentWiki?.id
    formState.title = documentWiki?.title ?? ''
    formState.content = documentWiki?.content ?? ''
    formState.summary = documentWiki?.summary ?? ''
    formState.tags = documentWiki?.tags ?? []
    formState.spaceId = documentWiki?.spaceId
    formState.folderId = documentWiki?.folderId ?? ''
    if (formState.spaceId) {
      fetchFolders(formState.spaceId)
    }
  },
  { immediate: true },
)

const fetchSpaces = async () => {
  const res = await listVisibleSpaceUsingGet()
  if (res.data.code === 0 && res.data.data) {
    spaces.value = res.data.data
    if (!formState.spaceId && spaces.value.length > 0) {
      formState.spaceId = spaces.value[0].id
      await fetchFolders(formState.spaceId)
    }
  } else {
    message.error('获取文档空间失败，' + res.data.message)
  }
}

const fetchFolders = async (spaceId: SelectValue) => {
  if (!spaceId) {
    folders.value = []
    return
  }
  folderLoading.value = true
  try {
    const res = await listFolderTreeUsingGet({ spaceId })
    if (res.data.code === 0) {
      folders.value = res.data.data ?? []
    } else {
      message.error('获取文件夹失败，' + res.data.message)
    }
  } finally {
    folderLoading.value = false
  }
}

const handleSpaceChange = async (spaceId: SelectValue) => {
  formState.folderId = ''
  await fetchFolders(spaceId)
}

const handleFinish = () => {
  if (!formState.spaceId) {
    message.warning('请选择文档空间')
    return
  }
  emit('submit', {
    ...formState,
    folderId: formState.folderId || undefined,
    tags: formState.tags ?? [],
  })
}

const spaceRegionName = (type?: number) => {
  if (type === 2) return '公开文档'
  if (type === 1) return '团队文档'
  return '个人文档'
}

const flattenFolders = (
  nodes: API.WikiFolderVis[],
  level = 0,
): { label: string; value: string | number | undefined }[] => {
  return nodes.flatMap((node) => [
    {
      label: `${'　'.repeat(level)}${node.name ?? node.id}`,
      value: node.id,
    },
    ...flattenFolders(node.children ?? [], level + 1),
  ])
}

onMounted(() => {
  fetchSpaces()
})
</script>
