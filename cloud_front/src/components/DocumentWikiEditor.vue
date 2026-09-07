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
      <div class="rich-editor">
        <div v-if="editor" class="editor-toolbar">
          <a-button size="small" :type="isActive('paragraph') ? 'primary' : 'default'" @click="setParagraph">
            正文
          </a-button>
          <a-button size="small" :type="isActive('heading', { level: 2 }) ? 'primary' : 'default'" @click="toggleHeading(2)">
            标题
          </a-button>
          <a-button size="small" :type="isActive('bold') ? 'primary' : 'default'" @click="toggleBold">
            B
          </a-button>
          <a-button size="small" :type="isActive('italic') ? 'primary' : 'default'" @click="toggleItalic">
            I
          </a-button>
          <a-button size="small" :type="isActive('bulletList') ? 'primary' : 'default'" @click="toggleBulletList">
            项目列表
          </a-button>
          <a-button size="small" :type="isActive('orderedList') ? 'primary' : 'default'" @click="toggleOrderedList">
            编号列表
          </a-button>
          <a-button size="small" :type="isActive('blockquote') ? 'primary' : 'default'" @click="toggleBlockquote">
            引用
          </a-button>
          <a-button size="small" @click="undo">撤销</a-button>
          <a-button size="small" @click="redo">重做</a-button>
        </div>
        <EditorContent :editor="editor" class="editor-content" />
      </div>
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import { listVisibleSpaceUsingGet } from '@/api/wikiSpaceController.ts'
import { listFolderTreeUsingGet } from '@/api/wikiFolderController.ts'
import { uploadWikiImageUsingPost } from '@/api/documentWikiController.ts'

type SelectValue = string | number | undefined

type InitialLocation = {
  spaceId?: SelectValue
  folderId?: SelectValue | null
}

const props = withDefaults(
  defineProps<{
    documentWiki?: API.DocumentWikiVis
    initialLocation?: InitialLocation
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

const editor = useEditor({
  content: '',
  extensions: [
    StarterKit,
    Image.configure({
      inline: false,
      allowBase64: false,
    }),
  ],
  editorProps: {
    attributes: {
      class: 'tiptap-editor',
    },
    handlePaste(_view, event) {
      const files = Array.from(event.clipboardData?.files ?? []).filter((file) =>
        file.type.startsWith('image/'),
      )
      if (!files.length) return false
      void insertUploadedImages(files)
      return true
    },
    handleDrop(_view, event) {
      const files = Array.from(event.dataTransfer?.files ?? []).filter((file) =>
        file.type.startsWith('image/'),
      )
      if (!files.length) return false
      event.preventDefault()
      void insertUploadedImages(files)
      return true
    },
  },
  onUpdate({ editor }) {
    formState.content = editor.getHTML()
  },
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
    formState.summary = documentWiki?.summary ?? ''
    formState.tags = documentWiki?.tags ?? []
    formState.spaceId = documentWiki?.spaceId
    formState.folderId = documentWiki?.folderId ?? ''
    setEditorContent(documentWiki?.content ?? '')
    if (formState.spaceId) {
      fetchFolders(formState.spaceId)
      return
    }
    applyInitialLocation()
  },
  { immediate: true },
)

watch(
  () => props.initialLocation,
  (initialLocation) => {
    if (formState.id) return
    applyInitialLocation(initialLocation)
  },
  { immediate: true },
)

function applyInitialLocation(initialLocation = props.initialLocation) {
  formState.spaceId = initialLocation?.spaceId
  formState.folderId = initialLocation?.folderId ?? ''
  if (formState.spaceId) {
    fetchFolders(formState.spaceId)
  }
}

function setEditorContent(content: string) {
  formState.content = content
  if (editor.value) {
    editor.value.commands.setContent(content || '', { emitUpdate: false })
  }
}

const fetchSpaces = async () => {
  const res = await listVisibleSpaceUsingGet()
  if (res.data.code === 0 && res.data.data) {
    spaces.value = res.data.data
    if (!formState.spaceId && spaces.value.length > 0) {
      formState.spaceId = props.initialLocation?.spaceId ?? spaces.value[0].id
      formState.folderId = props.initialLocation?.folderId ?? ''
      await fetchFolders(formState.spaceId)
    }
  } else {
    message.error('获取文档空间失败，' + res.data.message)
  }
}

async function fetchFolders(spaceId: SelectValue) {
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
  if (!editor.value || editor.value.isEmpty) {
    message.warning('请输入正文')
    return
  }
  emit('submit', {
    ...formState,
    content: editor.value.getHTML(),
    folderId: formState.folderId || undefined,
    tags: formState.tags ?? [],
    contentFormat: 'html',
  })
}

const insertUploadedImages = async (files: File[]) => {
  const urls = await uploadImages(files)
  urls.forEach((url) => {
    editor.value?.chain().focus().setImage({ src: url }).run()
  })
}

const uploadImages = async (files: File[]) => {
  if (!formState.spaceId) {
    message.warning('请先选择文档空间再上传图片')
    return []
  }
  const urls: string[] = []
  for (const file of files) {
    try {
      const res = (await uploadWikiImageUsingPost({ spaceId: formState.spaceId }, {}, file)) as {
        data: API.BaseResponseString_
      }
      if (res.data.code === 0 && res.data.data) {
        urls.push(res.data.data)
      } else {
        message.error('图片上传失败，' + res.data.message)
      }
    } catch (e: any) {
      message.error('图片上传失败，' + e.message)
    }
  }
  return urls
}

const setParagraph = () => editor.value?.chain().focus().setParagraph().run()
const toggleHeading = (level: 1 | 2 | 3) =>
  editor.value?.chain().focus().toggleHeading({ level }).run()
const toggleBold = () => editor.value?.chain().focus().toggleBold().run()
const toggleItalic = () => editor.value?.chain().focus().toggleItalic().run()
const toggleBulletList = () => editor.value?.chain().focus().toggleBulletList().run()
const toggleOrderedList = () => editor.value?.chain().focus().toggleOrderedList().run()
const toggleBlockquote = () => editor.value?.chain().focus().toggleBlockquote().run()
const undo = () => editor.value?.chain().focus().undo().run()
const redo = () => editor.value?.chain().focus().redo().run()
const isActive = (name: string, attributes?: Record<string, unknown>) =>
  Boolean(editor.value?.isActive(name, attributes))

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

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<style scoped>
.rich-editor {
  overflow: hidden;
  border: 1px solid var(--wiki-border, #d9d9d9);
  border-radius: 6px;
  background: var(--wiki-panel, #ffffff);
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  border-bottom: 1px solid var(--wiki-border, #d9d9d9);
  background: var(--wiki-panel-head, #fafafa);
}

.editor-content {
  min-height: 480px;
}

.editor-content :deep(.tiptap-editor) {
  min-height: 480px;
  padding: 16px;
  outline: none;
  color: var(--wiki-text, #251f18);
  line-height: 1.8;
}

.editor-content :deep(.tiptap-editor p) {
  margin: 0 0 12px;
}

.editor-content :deep(.tiptap-editor h1),
.editor-content :deep(.tiptap-editor h2),
.editor-content :deep(.tiptap-editor h3) {
  margin: 20px 0 12px;
  font-weight: 600;
  line-height: 1.35;
}

.editor-content :deep(.tiptap-editor blockquote) {
  margin: 14px 0;
  padding: 8px 12px;
  border-left: 3px solid var(--wiki-accent, #e07a2d);
  background: var(--wiki-accent-soft, #fff7e6);
  color: var(--wiki-text-muted, #7b6c5d);
}

.editor-content :deep(.tiptap-editor ul),
.editor-content :deep(.tiptap-editor ol) {
  margin: 0 0 12px 20px;
  padding-left: 18px;
}

.editor-content :deep(.tiptap-editor img) {
  max-width: 100%;
  border-radius: 4px;
}
</style>
