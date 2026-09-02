<template>
  <a-form layout="vertical" :model="formState" @finish="handleFinish">
    <a-form-item label="标题" name="title" :rules="[{ required: true, message: '请输入标题' }]">
      <a-input v-model:value="formState.title" placeholder="请输入文档标题" allow-clear />
    </a-form-item>
    <a-form-item label="分类" name="category">
      <a-input v-model:value="formState.category" placeholder="请输入分类" allow-clear />
    </a-form-item>
    <a-form-item label="标签" name="tags">
      <a-select
        v-model:value="formState.tags"
        mode="tags"
        placeholder="请输入标签"
        allow-clear
      />
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
import { reactive, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    documentWiki?: API.DocumentWikiVis
    submitText?: string
    loading?: boolean
  }>(),
  {
    submitText: '保存',
    loading: false,
  }
)

const emit = defineEmits<{
  submit: [values: API.DocumentWikiEditRequest]
  cancel: []
}>()

const formState = reactive<API.DocumentWikiEditRequest>({
  title: '',
  content: '',
  summary: '',
  category: '',
  tags: [],
})

watch(
  () => props.documentWiki,
  (documentWiki) => {
    formState.id = documentWiki?.id
    formState.title = documentWiki?.title ?? ''
    formState.content = documentWiki?.content ?? ''
    formState.summary = documentWiki?.summary ?? ''
    formState.category = documentWiki?.category ?? ''
    formState.tags = documentWiki?.tags ?? []
  },
  { immediate: true }
)

const handleFinish = () => {
  emit('submit', {
    ...formState,
    tags: formState.tags ?? [],
  })
}
</script>
