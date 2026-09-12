<template>
  <div class="ai-assistant-page" :class="{ 'with-doc': docPreview.open }">
    <div class="main-row">
      <div class="panel">
        <!-- 顶栏：范围选择 + 模式开关 -->
        <div class="scope-bar">
          <div class="scope-title">
            <RobotOutlined class="scope-icon" />
            <span>检索范围</span>
          </div>
          <a-checkbox
            :checked="allSpaces"
            :indeterminate="!allSpaces && selectedSpaceIds.length > 0"
            @change="toggleAllSpaces"
          >
            全部授权空间
          </a-checkbox>
          <a-checkbox-group
            v-model:value="selectedSpaceIds"
            :disabled="allSpaces"
            class="space-group"
            :options="spaceOptions"
          />
          <a-tag v-if="lastMeta" class="scope-tag" color="orange">
            本次检索：{{ lastMeta.effectiveSpaceIds?.length ?? 0 }} 个空间 /
            {{ lastMeta.authorizedDocCount ?? 0 }} 篇授权文档
          </a-tag>
          <div class="thinking-switch">
            <a-switch v-model:checked="deepThinking" size="small" />
            <span class="thinking-label">深度思考</span>
            <a-tooltip title="开启后使用思考型模型，推理更深入但等待更久；默认快速模型秒级响应">
              <QuestionCircleOutlined class="thinking-tip" />
            </a-tooltip>
          </div>
          <a-button type="link" size="small" class="open-api-btn" @click="openKeyDrawer">
            <ApiOutlined /> 开放 API
          </a-button>
        </div>

        <!-- 消息流 -->
        <div ref="messageListRef" class="message-list">
          <div v-if="messages.length === 0" class="empty-hint">
            <RobotOutlined class="empty-icon" />
            <p>问点什么吧，例如：</p>
            <div class="hint-chips">
              <a-tag v-for="q in sampleQuestions" :key="q" class="hint-chip" @click="quickAsk(q)">
                {{ q }}
              </a-tag>
            </div>
            <p class="hint-scope">
              回答仅基于所选空间内已授权文档，引用文号可点击在右侧打开原文。
            </p>
          </div>

          <div
            v-for="(msg, i) in messages"
            :key="i"
            class="message-row"
            :class="msg.role === 'user' ? 'from-user' : 'from-assistant'"
          >
            <div class="avatar">
              <UserOutlined v-if="msg.role === 'user'" />
              <RobotOutlined v-else />
            </div>
            <div class="bubble">
              <!-- 用户消息 -->
              <template v-if="msg.role === 'user'">
                <div class="text">{{ msg.content }}</div>
              </template>

              <!-- 助手消息 -->
              <template v-else>
                <!-- 思考过程折叠 -->
                <div v-if="msg.reasoning" class="reasoning">
                  <a-collapse ghost>
                    <a-collapse-panel key="r" header="深度思考">
                      <div class="reasoning-text">{{ msg.reasoning }}</div>
                    </a-collapse-panel>
                  </a-collapse>
                </div>
                <!-- 正文（含可点击角标） -->
                <div v-if="msg.content" class="text" v-html="renderAnswer(msg)"></div>
                <div v-else-if="msg.status === 'streaming'" class="typing">
                  <a-spin size="small" />
                  <span>{{ msg.reasoning ? '正在生成回答…' : '正在检索并思考…' }}</span>
                </div>
                <!-- 引用区 -->
                <div v-if="msg.citations?.length" class="citations">
                  <div class="citations-title">引用依据</div>
                  <div
                    v-for="c in msg.citations"
                    :key="c.index"
                    class="citation-item"
                    @click="openCitation(c)"
                  >
                    <span class="cite-index">[{{ c.index }}]</span>
                    <span class="cite-title">{{ c.docTitle }}</span>
                    <a-tag v-if="c.docNumber" color="volcano" class="cite-number">
                      {{ c.docNumber }}
                    </a-tag>
                  </div>
                </div>
                <!-- 耗时与 token 统计 -->
                <div v-if="msg.stats" class="stats-line">
                  <span v-if="msg.trace?.totalMs != null">总计 {{ formatMs(msg.trace.totalMs) }}</span>
                  <span v-if="msg.stats.thinkingMs != null">思考 {{ formatMs(msg.stats.thinkingMs) }}</span>
                  <span v-if="msg.stats.answerMs != null">回答 {{ formatMs(msg.stats.answerMs) }}</span>
                  <span v-if="msg.stats.usage">
                    tokens ↑{{ msg.stats.usage.promptTokens ?? '-' }} / ↓{{
                      msg.stats.usage.completionTokens ?? '-'
                    }}
                  </span>
                  <span v-if="msg.stats.deepThinking" class="stats-mode">深度思考模式</span>
                </div>
                <!-- 调用明细：分步耗时时间线（默认收起） -->
                <div v-if="msg.trace" class="trace-panel">
                  <a-collapse ghost>
                    <a-collapse-panel key="t" :header="`调用明细 · 总耗时 ${formatMs(msg.trace.totalMs ?? 0)}`">
                      <div class="trace-timeline">
                        <div class="trace-row trace-time">开始 {{ formatClock(msg.trace.startedAt) }}</div>
                        <div v-for="s in traceSteps(msg)" :key="s.label" class="trace-row">
                          <span class="trace-label">{{ s.label }}</span>
                          <span class="trace-value" :class="{ skipped: s.skipped }">
                            {{ s.skipped ? '跳过' : formatMs(s.ms ?? 0) }}
                          </span>
                        </div>
                        <div class="trace-row trace-end">结束 {{ formatClock(msg.trace.finishedAt) }}</div>
                      </div>
                    </a-collapse-panel>
                  </a-collapse>
                </div>
                <div v-if="msg.stopped" class="stopped-text">已手动停止生成</div>
                <div v-if="msg.status === 'error'" class="error-text">
                  <WarningOutlined /> {{ msg.error }}
                </div>
                <div v-if="msg.truncated" class="error-text">
                  <WarningOutlined /> 回答因长度达到上限被截断，请尝试缩小问题范围或重新提问
                </div>
              </template>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="input-bar">
          <a-textarea
            v-model:value="inputText"
            class="input"
            placeholder="输入政策相关问题，Enter 发送，Shift+Enter 换行"
            :auto-size="{ minRows: 1, maxRows: 4 }"
            :disabled="streaming"
            @keydown.enter.exact.prevent="send"
          />
          <a-button
            v-if="streaming"
            class="stop-btn"
            danger
            @click="stopStream"
          >
            停止
          </a-button>
          <a-button
            v-else
            type="primary"
            class="send-btn"
            :disabled="!inputText.trim()"
            @click="send"
          >
            发送
          </a-button>
        </div>
      </div>

      <!-- 右侧文档预览栏 -->
      <div class="doc-panel" :class="{ open: docPreview.open }">
        <div class="doc-panel-inner">
          <div class="doc-panel-head">
            <div class="doc-panel-title">
              <FileTextOutlined class="doc-panel-icon" />
              <span class="doc-title-text" :title="docPreview.title">{{ docPreview.title }}</span>
              <a-tag v-if="docPreview.docNumber" color="volcano" class="doc-number-tag">
                {{ docPreview.docNumber }}
              </a-tag>
            </div>
            <a-button type="text" size="small" @click="closeDocPanel">
              <CloseOutlined />
            </a-button>
          </div>
          <a-spin :spinning="docPreview.loading" wrapper-class-name="doc-panel-body-wrap">
            <div ref="docPanelBodyRef" class="doc-panel-body">
              <DocumentWikiContentViewer
                v-if="docPreview.content"
                :content="docPreview.content"
                :content-format="docPreview.contentFormat"
              />
              <div v-else-if="!docPreview.loading" class="doc-empty">文档内容为空</div>
            </div>
          </a-spin>
        </div>
      </div>
    </div>

    <!-- 开放 API Key 管理抽屉 -->
    <a-drawer
      v-model:open="keyDrawer.open"
      title="开放 API"
      placement="right"
      :width="480"
    >
      <p class="key-desc">
        为外部程序（如本地 Agent）签发只读检索 Key。Key 只能访问<b>你可见的空间</b>，
        删除即立即失效。明文 Key 仅创建时展示一次。
      </p>

      <!-- 创建 -->
      <div class="key-create-bar">
        <a-input
          v-model:value="keyDrawer.newName"
          placeholder="Key 用途名，如：本地 Agent"
          :maxlength="64"
          @keydown.enter="createKey"
        />
        <a-button type="primary" :loading="keyDrawer.creating" @click="createKey">
          创建
        </a-button>
      </div>

      <!-- 明文一次性展示 -->
      <a-alert
        v-if="keyDrawer.createdKey"
        type="success"
        show-icon
        class="key-plaintext-alert"
      >
        <template #message>
          Key「{{ keyDrawer.createdKey.keyName }}」已创建：
          <div class="key-plaintext-row">
            <code class="key-plaintext">{{ keyDrawer.createdKey.apiKey }}</code>
            <a-button size="small" type="primary" @click="copyText(keyDrawer.createdKey.apiKey ?? '', 'Key')">
              复制
            </a-button>
          </div>
          <div class="key-plaintext-warn">⚠️ 关闭后无法再次查看，请立即保存</div>
        </template>
      </a-alert>

      <!-- 列表 -->
      <div class="key-list">
        <div v-if="keyDrawer.keys.length === 0 && !keyDrawer.loading" class="key-empty">
          暂无 API Key
        </div>
        <div v-for="k in keyDrawer.keys" :key="k.id" class="key-item">
          <div class="key-item-main">
            <div class="key-item-name">{{ k.keyName }}</div>
            <div class="key-item-meta">
              <code>{{ k.keyPrefix }}…</code>
              <span v-if="k.createTime">{{ formatKeyTime(k.createTime) }}</span>
            </div>
          </div>
          <a-popconfirm title="删除后立即失效，确定？" @confirm="deleteKey(k.id!)">
            <a-button type="link" danger size="small">删除</a-button>
          </a-popconfirm>
        </div>
      </div>

      <!-- 调用示例 -->
      <a-collapse ghost class="curl-collapse">
        <a-collapse-panel key="curl" header="curl 调用示例">
          <div class="curl-block">
            <div class="curl-title">只读检索</div>
            <div class="curl-row">
              <pre class="curl-code">{{ curlSearchExample }}</pre>
              <a-button size="small" @click="copyText(curlSearchExample, '示例')">复制</a-button>
            </div>
            <div class="curl-title">流式问答（SSE）</div>
            <div class="curl-row">
              <pre class="curl-code">{{ curlAskExample }}</pre>
              <a-button size="small" @click="copyText(curlAskExample, '示例')">复制</a-button>
            </div>
            <div class="curl-tip">
              Windows 终端直接内联中文可能出现编码错误，建议把 JSON 写入 UTF-8 文件后用
              <code>--data-binary @body.json</code> 发送。
            </div>
          </div>
        </a-collapse-panel>
      </a-collapse>
    </a-drawer>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  ApiOutlined,
  CloseOutlined,
  FileTextOutlined,
  QuestionCircleOutlined,
  RobotOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { message as antMessage } from 'ant-design-vue'
import {
  ragAskUsingStream,
  type RagAskMeta,
  type RagAskUsage,
  type RagCitation,
  type RagSearchTimings,
} from '@/api/ragController'
import {
  createRagApiKeyUsingPost,
  deleteRagApiKeyUsingPost,
  listRagApiKeysUsingGet,
  apiBaseUrl,
} from '@/api/ragController'
import { listVisibleSpaceUsingGet } from '@/api/wikiSpaceController'
import { getDocumentWikiVisByIdUsingGet } from '@/api/documentWikiController'
import DocumentWikiContentViewer from '@/components/DocumentWikiContentViewer.vue'

// ---- 检索范围 ----
interface SpaceOption {
  label: string
  value: number
}
const spaces = ref<SpaceOption[]>([])
const selectedSpaceIds = ref<number[]>([])
const allSpaces = ref(true)
const deepThinking = ref(false)

const spaceOptions = computed(() => spaces.value)

const toggleAllSpaces = () => {
  allSpaces.value = !allSpaces.value
  if (!allSpaces.value && selectedSpaceIds.value.length === 0 && spaces.value.length > 0) {
    // 从"全部"切到手选时默认保留全部勾选，便于用户做减法
    selectedSpaceIds.value = spaces.value.map((s) => s.value)
  }
}

const loadSpaces = async () => {
  try {
    const res = await listVisibleSpaceUsingGet()
    const list = res.data.data ?? []
    spaces.value = list
      .filter((s) => s.id != null)
      .map((s) => ({
        label: spaceLabel(s.name ?? '未命名空间', s.type),
        value: Number(s.id),
      }))
  } catch (e) {
    antMessage.error('加载可见空间失败')
  }
}

const spaceLabel = (name: string, type?: number) => {
  if (type === 0) return `${name}（个人）`
  if (type === 1) return `${name}（团队）`
  if (type === 2) return `${name}（公开）`
  return name
}

// ---- 消息 ----
interface ChatStats {
  thinkingMs?: number
  answerMs?: number
  usage?: RagAskUsage | null
  deepThinking?: boolean
}
/** 一轮问答的调用明细（SSE done 携带的分步耗时时间线） */
interface ChatTrace {
  startedAt?: number
  finishedAt?: number
  totalMs?: number
  retrievalMs?: number
  promptMs?: number
  firstTokenMs?: number | null
  llmMs?: number
  steps?: RagSearchTimings | null
}
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  reasoning?: string
  citations?: RagCitation[]
  status?: 'streaming' | 'done' | 'error'
  error?: string
  truncated?: boolean
  stopped?: boolean
  stats?: ChatStats
  trace?: ChatTrace
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const lastMeta = ref<RagAskMeta | null>(null)
const messageListRef = ref<HTMLElement>()
const abortController = ref<AbortController | null>(null)

const sampleQuestions = [
  '渝府办发〔2026〕24号说了什么',
  '重庆如何推动新场景大规模应用',
  '低保申请的条件是什么',
]

const quickAsk = (q: string) => {
  inputText.value = q
  send()
}

const scrollToBottom = async () => {
  await nextTick()
  messageListRef.value?.scrollTo({
    top: messageListRef.value.scrollHeight,
    behavior: 'smooth',
  })
}

const formatMs = (ms: number) => {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

/** epoch ms -> HH:mm:ss.mmm（本地时区），用于时间线的开始/结束时间 */
const formatClock = (epochMs?: number | string) => {
  // 后端把 Long 序列化为字符串，必须 Number() 归一化，否则 new Date("1789…") 是 Invalid Date
  const n = Number(epochMs)
  if (epochMs == null || !Number.isFinite(n)) return '-'
  const d = new Date(n)
  const pad = (v: number, w = 2) => String(v).padStart(w, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
}

/** 明细面板的步骤行：固定顺序；值为 null 表示该步未执行（如文号命中占满 topK 跳过向量化） */
const traceSteps = (msg: ChatMessage): { label: string; ms?: number | null; skipped: boolean }[] => {
  const t = msg.trace
  if (!t) return []
  const s = t.steps ?? {}
  const rows = [
    { label: '检索 · 权限范围过滤', ms: s.permissionMs },
    { label: '检索 · 授权文档计数', ms: s.docCountMs },
    { label: '检索 · 文号精确匹配', ms: s.docNumberMs },
    { label: '检索 · 查询向量化（embedding）', ms: s.embedMs },
    { label: '检索 · 向量相似搜索', ms: s.vectorMs },
    { label: '构造引用与提示词', ms: t.promptMs },
    { label: 'LLM 首 token', ms: t.firstTokenMs },
  ]
  return rows.map((r) => ({ ...r, skipped: r.ms == null }))
}

// ---- 发送与流式接收 ----
const send = async () => {
  const query = inputText.value.trim()
  if (!query || streaming.value) return
  inputText.value = ''
  streaming.value = true
  abortController.value = new AbortController()

  messages.value.push({ role: 'user', content: query })
  // reactive() 是必须的：push 进响应式数组的是原始引用，后续 content += 若直接改原始对象
  // 会绕过 Vue3 Proxy，流式 delta 不触发重渲染（表现为文字积压、点一下才整段蹦出）
  const assistantMsg: ChatMessage = reactive({
    role: 'assistant',
    content: '',
    status: 'streaming',
  })
  messages.value.push(assistantMsg)
  await scrollToBottom()

  const body = {
    query,
    spaceIds: allSpaces.value ? undefined : selectedSpaceIds.value,
    deepThinking: deepThinking.value || undefined,
  }

  try {
    await ragAskUsingStream(
      body,
      {
        onMeta: (meta) => {
          lastMeta.value = meta
          assistantMsg.citations = meta.citations ?? []
        },
        onReason: (text) => {
          assistantMsg.reasoning = (assistantMsg.reasoning ?? '') + text
        },
        onDelta: (text) => {
          assistantMsg.content += text
          scrollToBottom()
        },
        onDone: (done) => {
          assistantMsg.status = 'done'
          if (done.finishReason === 'length') {
            assistantMsg.truncated = true
          }
          assistantMsg.stats = {
            thinkingMs: done.thinkingMs ?? undefined,
            answerMs: done.answerMs ?? undefined,
            usage: done.usage ?? null,
            deepThinking: deepThinking.value,
          }
          // 后端 Long 一律序列化为字符串（防 JS 精度丢失），前端统一 Number() 归一化；
          // steps 里 null 表示该步未执行（如文号命中占满 topK 跳过向量化），必须保留 null 而非 Number(null)=0
          const num = (v?: number | string | null): number | null | undefined =>
            v == null ? v : Number(v)
          assistantMsg.trace = {
            startedAt: num(done.startedAt) ?? undefined,
            finishedAt: num(done.finishedAt) ?? undefined,
            totalMs: num(done.totalMs) ?? undefined,
            retrievalMs: num(done.retrievalMs) ?? undefined,
            promptMs: num(done.promptMs) ?? undefined,
            firstTokenMs: num(done.firstTokenMs),
            llmMs: num(done.llmMs) ?? undefined,
            steps: {
              permissionMs: num(done.steps?.permissionMs),
              docCountMs: num(done.steps?.docCountMs),
              docNumberMs: num(done.steps?.docNumberMs),
              embedMs: num(done.steps?.embedMs),
              vectorMs: num(done.steps?.vectorMs),
              totalMs: num(done.steps?.totalMs),
            },
          }
        },
        onError: (msg) => {
          assistantMsg.status = 'error'
          assistantMsg.error = msg
        },
      },
      abortController.value.signal,
    )
    if (assistantMsg.status === 'streaming') {
      assistantMsg.status = 'done'
    }
  } catch (e: any) {
    if (e?.name === 'AbortError') {
      assistantMsg.status = 'done'
      assistantMsg.stopped = true
    } else {
      assistantMsg.status = 'error'
      assistantMsg.error = e?.message ?? '网络异常，请稍后重试'
    }
  } finally {
    streaming.value = false
    abortController.value = null
    scrollToBottom()
  }
}

const stopStream = () => {
  abortController.value?.abort()
}

// ---- 渲染 ----
/**
 * 将回答渲染为 HTML：先转义，再把 [n] 角标替换为可点击标记。
 * 引用文号一律来自 meta.citations 元数据，不信任 LLM 文本中的文号。
 */
const renderAnswer = (msg: ChatMessage) => {
  const citations = msg.citations ?? []
  const escaped = escapeHtml(msg.content)
  return escaped.replace(/\[(\d+)\]/g, (raw, numStr) => {
    const num = Number(numStr)
    const hit = citations.find((c) => c.index === num)
    if (!hit) return raw
    return `<sup class="cite-marker" data-index="${hit.index}">[${num}]</sup>`
  })
}

const escapeHtml = (text: string) =>
  text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')

// ---- 右侧文档预览（双栏） ----
const docPanelBodyRef = ref<HTMLElement>()
const docPreview = ref<{
  open: boolean
  loading: boolean
  docId?: number
  title: string
  docNumber?: string
  content?: string
  contentFormat?: string
}>({
  open: false,
  loading: false,
  title: '',
})

/** 点击引用/角标：右侧打开文档并高亮引用切片 */
const openCitation = (citation: RagCitation) => {
  openDocPanel(citation.docId, citation.docTitle, citation.docNumber, citation.chunkText)
}

const openDocPanel = async (
  docId: number,
  title: string,
  docNumber?: string,
  chunkText?: string,
) => {
  if (!docId) return
  docPreview.value = {
    open: true,
    loading: true,
    docId,
    title: title || `文档 ${docId}`,
    docNumber,
  }
  try {
    const res = await getDocumentWikiVisByIdUsingGet({ id: docId })
    const doc = res.data.data
    docPreview.value.content = doc?.content ?? ''
    docPreview.value.contentFormat = doc?.contentFormat ?? 'plain'
    docPreview.value.loading = false
    if (chunkText) {
      // MdPreview 渲染是异步的，等一拍再定位高亮
      setTimeout(() => highlightChunk(chunkText), 300)
    }
  } catch (e) {
    docPreview.value.loading = false
    docPreview.value.content = ''
    antMessage.error('文档加载失败或无权限查看')
  }
}

/** 跳过 front-matter，取正文第一段可定位文本 */
const locateSnippet = (chunkText: string): string => {
  let text = chunkText ?? ''
  if (text.startsWith('---')) {
    const end = text.indexOf('\n---', 3)
    if (end > 0) text = text.slice(end + 4)
  }
  const lines = text
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l.length >= 12 && !l.startsWith('#') && !l.startsWith('---'))
  return lines[0] ?? text.trim().slice(0, 40)
}

/** 在右侧渲染结果中找到引用切片所在段落，黄色高亮并滚动定位 */
const highlightChunk = (chunkText: string) => {
  const container = docPanelBodyRef.value
  if (!container) return
  const snippet = locateSnippet(chunkText)
  if (!snippet) return
  const blocks = container.querySelectorAll('p, li, h1, h2, h3, h4, h5, h6, blockquote, td')
  for (const el of Array.from(blocks)) {
    const t = (el as HTMLElement).textContent ?? ''
    if (t.includes(snippet.slice(0, 24))) {
      el.classList.add('chunk-highlight')
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      return
    }
  }
}

const closeDocPanel = () => {
  docPreview.value.open = false
}

// v-html 内角标点击：事件委托
const onMarkerClick = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (target.classList?.contains('cite-marker')) {
    const index = Number(target.dataset.index)
    const hit = lastMeta.value?.citations?.find((c) => c.index === index)
    if (hit) {
      openCitation(hit)
      return
    }
  }
}

// ---- 开放 API Key 管理 ----
const keyDrawer = ref<{
  open: boolean
  loading: boolean
  creating: boolean
  newName: string
  keys: API.RagApiKeyView[]
  createdKey: API.RagApiKeyCreatedView | null
}>({
  open: false,
  loading: false,
  creating: false,
  newName: '',
  keys: [],
  createdKey: null,
})

const openKeyDrawer = () => {
  keyDrawer.value.open = true
  loadKeys()
}

const loadKeys = async () => {
  keyDrawer.value.loading = true
  try {
    const res = await listRagApiKeysUsingGet()
    keyDrawer.value.keys = res.data.data ?? []
  } catch (e) {
    antMessage.error('加载 Key 列表失败')
  } finally {
    keyDrawer.value.loading = false
  }
}

const createKey = async () => {
  if (keyDrawer.value.creating) return
  keyDrawer.value.creating = true
  try {
    const res = await createRagApiKeyUsingPost(keyDrawer.value.newName.trim() || '默认 Key')
    keyDrawer.value.createdKey = res.data.data ?? null
    keyDrawer.value.newName = ''
    await loadKeys()
  } catch (e) {
    antMessage.error('创建 Key 失败')
  } finally {
    keyDrawer.value.creating = false
  }
}

const deleteKey = async (id: number | string) => {
  try {
    await deleteRagApiKeyUsingPost(Number(id))
    antMessage.success('Key 已删除并立即失效')
    if (keyDrawer.value.createdKey && String(keyDrawer.value.createdKey.id) === String(id)) {
      keyDrawer.value.createdKey = null
    }
    await loadKeys()
  } catch (e) {
    antMessage.error('删除失败')
  }
}

const formatKeyTime = (time: string) => {
  try {
    return new Date(time).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return time
  }
}

const copyText = async (text: string, label: string) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    antMessage.success(`${label}已复制`)
  } catch {
    // 降级：老式 execCommand
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    antMessage.success(`${label}已复制`)
  }
}

const apiBase = apiBaseUrl
const curlSearchExample = `curl -X POST ${apiBase}/api/open/rag/search \\
  -H "Content-Type: application/json; charset=utf-8" \\
  -H "X-API-Key: cpk_你的Key" \\
  --data-binary @body.json`

const curlAskExample = `curl -N -X POST ${apiBase}/api/open/rag/ask \\
  -H "Content-Type: application/json; charset=utf-8" \\
  -H "X-API-Key: cpk_你的Key" \\
  --data-binary @body.json`

onMounted(() => {
  loadSpaces()
  messageListRef.value?.addEventListener('click', onMarkerClick)
})
</script>

<style scoped>
.ai-assistant-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px 16px 60px;
  transition: max-width 0.35s ease;
}

.ai-assistant-page.with-doc {
  max-width: 1400px;
}

.main-row {
  display: flex;
  gap: 14px;
  align-items: stretch;
}

.panel {
  background: #fffdf8;
  border: 1px solid #e8dcc8;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(120, 90, 40, 0.08);
  display: flex;
  flex-direction: column;
  height: calc(100vh - 190px);
  min-height: 480px;
  flex: 1;
  min-width: 0;
}

/* 右侧文档预览栏：宽度过渡实现单栏→双栏动画 */
.doc-panel {
  flex: 0 0 0%;
  width: 0;
  opacity: 0;
  min-width: 0;
  overflow: hidden;
  transition: flex-basis 0.35s ease, opacity 0.3s ease 0.1s;
}

.doc-panel.open {
  flex: 0 0 46%;
  opacity: 1;
}

.doc-panel-inner {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 190px);
  min-height: 480px;
  background: #fffdf8;
  border: 1px solid #e8dcc8;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(120, 90, 40, 0.08);
  overflow: hidden;
}

.doc-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #efe4d0;
  background: #faf4e8;
}

.doc-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.doc-panel-icon {
  color: #e07a2d;
  flex: 0 0 auto;
}

.doc-title-text {
  font-weight: 600;
  color: #6b4f1d;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-number-tag {
  flex: 0 0 auto;
}

.doc-panel-body-wrap {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* a-spin 内部两层容器需要透传高度，否则内部文档区拿不到约束高度、无法滚动 */
.doc-panel-body-wrap :deep(.ant-spin-nested-loading) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.doc-panel-body-wrap :deep(.ant-spin-container) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.doc-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
}

.doc-empty {
  text-align: center;
  color: #a89a7f;
  padding-top: 60px;
}

/* 引用切片黄色高亮 */
:deep(.chunk-highlight) {
  background: #ffe98a;
  border-radius: 4px;
  box-shadow: 0 0 0 3px #ffe98a;
  transition: background 0.6s ease;
}

/* 范围栏 */
.scope-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #efe4d0;
  background: #faf4e8;
  border-radius: 12px 12px 0 0;
}

.scope-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #6b4f1d;
}

.scope-icon {
  color: #e07a2d;
}

.space-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.scope-tag {
  margin-left: auto;
}

.thinking-switch {
  display: flex;
  align-items: center;
  gap: 6px;
}

.thinking-label {
  font-size: 13px;
  color: #6b4f1d;
}

.thinking-tip {
  font-size: 12px;
  color: #a89a7f;
  cursor: help;
}

/* 消息区 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 18px 16px;
}

.empty-hint {
  text-align: center;
  color: #8a7a5f;
  padding-top: 60px;
}

.empty-icon {
  font-size: 44px;
  color: #e0a86d;
}

.hint-chips {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0 16px;
}

.hint-chip {
  cursor: pointer;
  user-select: none;
}

.hint-scope {
  font-size: 12px;
  color: #a89a7f;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.from-user {
  flex-direction: row-reverse;
}

.avatar {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #fff;
}

.from-user .avatar {
  background: #5b7a9d;
}

.from-assistant .avatar {
  background: #e07a2d;
}

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.7;
}

.from-user .bubble {
  background: #dce8f5;
  border-top-right-radius: 2px;
}

.from-assistant .bubble {
  background: #f7efe0;
  border-top-left-radius: 2px;
  flex: 1;
  max-width: 92%;
}

.text {
  white-space: pre-wrap;
  word-break: break-word;
  color: #4a3b22;
}

.typing {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #a08c68;
  font-size: 13px;
}

/* 思考过程 */
.reasoning {
  margin-bottom: 6px;
}

.reasoning :deep(.ant-collapse-header) {
  padding: 2px 0 !important;
  color: #a08c68 !important;
  font-size: 12px;
}

.reasoning-text {
  color: #a08c68;
  font-size: 12px;
  white-space: pre-wrap;
  max-height: 180px;
  overflow-y: auto;
}

/* 引用区 */
.citations {
  margin-top: 10px;
  border-top: 1px dashed #d8c9ab;
  padding-top: 8px;
}

.citations-title {
  font-size: 12px;
  color: #8a7a5f;
  margin-bottom: 6px;
}

.citation-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #5c4d2e;
}

.citation-item:hover {
  background: #f0e6d2;
}

.cite-index {
  color: #e07a2d;
  font-weight: 600;
}

.cite-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cite-number {
  flex: 0 0 auto;
}

:deep(.cite-marker) {
  color: #e07a2d;
  cursor: pointer;
  font-weight: 600;
  margin: 0 1px;
}

:deep(.cite-marker:hover) {
  text-decoration: underline;
}

/* 统计行 */
.stats-line {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: #a08c68;
}

.stats-mode {
  color: #e07a2d;
}

/* 调用明细时间线 */
.trace-panel {
  margin-top: 2px;
}

.trace-panel :deep(.ant-collapse-header) {
  padding: 2px 0 !important;
  color: #a08c68 !important;
  font-size: 12px;
}

.trace-timeline {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12px;
}

.trace-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.trace-label {
  color: #8a7a5f;
}

.trace-value {
  color: #6b4f1d;
  font-variant-numeric: tabular-nums;
}

.trace-value.skipped {
  color: #c9bda3;
}

.trace-time {
  color: #a08c68;
}

.trace-end {
  color: #a08c68;
  border-top: 1px dashed #d8c9ab;
  padding-top: 4px;
  margin-top: 4px;
}

.stopped-text {
  margin-top: 6px;
  font-size: 12px;
  color: #a08c68;
}

.error-text {
  color: #c0392b;
  font-size: 13px;
  margin-top: 6px;
}

/* 输入区 */
.input-bar {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 16px;
  border-top: 1px solid #efe4d0;
  background: #faf4e8;
  border-radius: 0 0 12px 12px;
}

.input {
  flex: 1;
}

.send-btn {
  background: #e07a2d;
  border-color: #e07a2d;
  min-width: 84px;
}

.stop-btn {
  min-width: 84px;
}

/* 开放 API */
.open-api-btn {
  padding: 0 4px;
  color: #a14f16;
  font-size: 13px;
}

.key-desc {
  font-size: 13px;
  color: #6b5b3e;
  line-height: 1.8;
}

.key-create-bar {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}

.key-plaintext-alert {
  margin-bottom: 12px;
}

.key-plaintext-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.key-plaintext {
  flex: 1;
  word-break: break-all;
  font-size: 12px;
  background: #f6f1e4;
  padding: 6px 8px;
  border-radius: 4px;
}

.key-plaintext-warn {
  margin-top: 6px;
  font-size: 12px;
  color: #c0392b;
}

.key-list {
  margin-bottom: 16px;
}

.key-empty {
  text-align: center;
  color: #a89a7f;
  padding: 20px 0;
}

.key-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 8px;
  border-bottom: 1px dashed #e8dcc8;
}

.key-item-main {
  flex: 1;
  min-width: 0;
}

.key-item-name {
  font-weight: 600;
  color: #4a3b22;
  font-size: 14px;
}

.key-item-meta {
  display: flex;
  gap: 10px;
  margin-top: 2px;
  font-size: 12px;
  color: #a08c68;
}

.key-item-meta code {
  background: #f6f1e4;
  padding: 1px 5px;
  border-radius: 3px;
}

.curl-collapse {
  margin-top: 8px;
}

.curl-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b4f1d;
  margin: 10px 0 4px;
}

.curl-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.curl-code {
  flex: 1;
  font-size: 11px;
  line-height: 1.6;
  background: #f6f1e4;
  border: 1px solid #e8dcc8;
  border-radius: 4px;
  padding: 8px;
  overflow-x: auto;
  white-space: pre;
  margin: 0;
}

.curl-tip {
  margin-top: 8px;
  font-size: 11px;
  color: #8c8c8c;
  line-height: 1.6;
}

.curl-tip code {
  padding: 1px 4px;
  background: #f6f1e4;
  border-radius: 3px;
  font-size: 10px;
}

@media (max-width: 760px) {
  .bubble {
    max-width: 88%;
  }

  .from-assistant .bubble {
    max-width: 88%;
  }

  .doc-panel.open {
    display: none;
  }
}
</style>
