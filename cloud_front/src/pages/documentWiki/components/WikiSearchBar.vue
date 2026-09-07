<template>
  <a-form layout="inline" :model="searchParams" class="search-form" @finish="emit('search')">
    <a-form-item label="关键词" name="searchText">
      <a-input
        v-model:value="searchParams.searchText"
        placeholder="搜索标题或正文"
        allow-clear
        @change="emit('text-change')"
      />
    </a-form-item>
    <a-form-item label="匹配模式" name="matchMode">
      <a-radio-group v-model:value="searchParams.matchMode">
        <a-radio-button value="title">标题</a-radio-button>
        <a-radio-button value="content">正文</a-radio-button>
        <a-radio-button value="titleOrContent">标题或正文</a-radio-button>
      </a-radio-group>
    </a-form-item>
    <a-form-item label="空间" name="spaceId">
      <a-select
        v-model:value="searchParams.spaceId"
        placeholder="全部可见空间"
        style="min-width: 180px"
        allow-clear
        :options="allSpaceOptions"
      />
    </a-form-item>
    <a-form-item>
      <a-space>
        <a-button type="primary" html-type="submit">搜索</a-button>
        <a-button html-type="button" @click="emit('create')">创建文档</a-button>
      </a-space>
    </a-form-item>
  </a-form>
</template>
<script setup lang="ts">
import type { IdValue } from './wikiShared'
const searchParams = defineModel<API.DocumentWikiQueryRequest>({ required: true })
defineProps<{ allSpaceOptions: { label: string; value: IdValue }[] }>()
const emit = defineEmits<{ search: []; 'text-change': []; create: [] }>()
</script>
<style scoped>
.search-form {
  margin-bottom: 16px;
  row-gap: 12px;
}
</style>
