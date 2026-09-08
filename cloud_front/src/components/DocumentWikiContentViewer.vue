<template>
  <MdPreview
    v-if="normalizedFormat === 'markdown'"
    :model-value="content"
    language="zh-CN"
    :md-heading-id="headingId"
    class="markdown-preview"
  />
  <iframe
    v-else-if="normalizedFormat === 'html'"
    class="html-preview-frame"
    title="HTML 文档预览"
    sandbox="allow-same-origin"
    :srcdoc="htmlSrcDoc"
  />
  <div v-else class="plain-content">{{ content }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

const props = defineProps<{
  content?: string
  contentFormat?: string
}>()

const normalizedFormat = computed(() => props.contentFormat || 'plain')
const content = computed(() => props.content ?? '')

// The Wiki outline column addresses headings as `wiki-heading-<index>` in document order, so the
// rendered Markdown has to emit the exact same ids instead of md-editor's text based default.
const headingId = ({ index }: { text: string; level: number; index: number }) =>
  `wiki-heading-${index}`

// Uploaded HTML is rendered as an isolated document instead of being injected into the Wiki page:
// `sandbox` without `allow-scripts` keeps page-level <style>, class names and layout while making
// sure uploaded scripts never run against the application. `allow-same-origin` is only needed so
// the outline column can scroll headings inside the frame; it grants no script permission.
const htmlSrcDoc = computed(() => {
  const raw = content.value
  if (!raw) return ''
  if (/<meta[^>]+charset=/i.test(raw)) return raw
  const metaCharset = '<meta charset="utf-8">'
  return /<head[^>]*>/i.test(raw)
    ? raw.replace(/<head([^>]*)>/i, `<head$1>${metaCharset}`)
    : metaCharset + raw
})
</script>

<style scoped>
.html-preview-frame,
.plain-content {
  width: 100%;
  line-height: 1.8;
  color: var(--wiki-text);
}

.html-preview-frame {
  height: 70vh;
  min-height: 360px;
  border: 1px solid var(--wiki-border);
  border-radius: 6px;
  background: #fff;
}

.plain-content {
  white-space: pre-wrap;
}

/* ---- Warm-theme overrides for the Markdown preview -----------------
   md-editor-v3 ships a pure-white light theme. The library declares
   `--md-bk-color: #fff` and `background-color` on the `.md-editor` root
   element itself, so overriding only the inner wrappers still leaves a
   white box. `.markdown-preview` lands on that same root element, and the
   scoped attribute gives these declarations higher specificity, so the
   warm variables win. */
.markdown-preview {
  --md-bk-color: transparent;
  --md-color: var(--wiki-text);
  --md-border-color: transparent;
  --md-bk-color-outstand: var(--wiki-muted);
  --md-bk-hover-color: var(--wiki-muted);
  --md-hover-color: var(--wiki-text);
  border: none;
  background: transparent;
  color: var(--wiki-text);
}
.markdown-preview :deep(.md-editor-preview-wrapper),
.markdown-preview :deep(.md-editor-preview) {
  background: transparent;
  color: var(--wiki-text);
  font-size: 15px;
}

.markdown-preview :deep(.md-editor-preview h1),
.markdown-preview :deep(.md-editor-preview h2),
.markdown-preview :deep(.md-editor-preview h3),
.markdown-preview :deep(.md-editor-preview h4),
.markdown-preview :deep(.md-editor-preview h5),
.markdown-preview :deep(.md-editor-preview h6) {
  color: var(--wiki-text);
  border-bottom-color: var(--wiki-border);
}

.markdown-preview :deep(.md-editor-preview h1),
.markdown-preview :deep(.md-editor-preview h2) {
  border-bottom: 1px solid var(--wiki-border);
  padding-bottom: 6px;
}

.markdown-preview :deep(.md-editor-preview a) {
  color: #a14f16;
}

.markdown-preview :deep(.md-editor-preview blockquote) {
  background: var(--wiki-accent-soft);
  border-left: 4px solid var(--wiki-accent);
  color: var(--wiki-text-muted);
}

.markdown-preview :deep(.md-editor-preview code) {
  background: var(--wiki-muted);
  color: #8a4b12;
}

.markdown-preview :deep(.md-editor-preview pre),
.markdown-preview :deep(.md-editor-preview pre code) {
  background: var(--wiki-muted);
  color: var(--wiki-text);
}

.markdown-preview :deep(.md-editor-preview pre) {
  border: 1px solid var(--wiki-border);
}

.markdown-preview :deep(.md-editor-preview table) {
  border-collapse: collapse;
}

.markdown-preview :deep(.md-editor-preview th),
.markdown-preview :deep(.md-editor-preview td) {
  border: 1px solid var(--wiki-border);
  background: transparent;
  color: var(--wiki-text);
}

.markdown-preview :deep(.md-editor-preview th) {
  background: var(--wiki-panel-head);
}

.markdown-preview :deep(.md-editor-preview tr:nth-child(2n)) {
  background: var(--wiki-muted);
}

.markdown-preview :deep(.md-editor-preview hr) {
  border-color: var(--wiki-border);
}
</style>
