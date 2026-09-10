// @ts-ignore
/* eslint-disable */
// API 手写模块：RAG 问答（SSE 流式走 fetch，axios 不支持流式读取）
import request from '@/request'

/**
 * 真实后端地址（axios baseURL）。开放 API 的 curl 示例必须指向它，
 * 而不是 window.location.origin（前端 dev server 没有 /api 代理，纯 HTTP 客户端访问会 404）。
 */
export const apiBaseUrl = (request.defaults.baseURL || window.location.origin) as string

/** 检索命中 / 引用元数据 */
export interface RagCitation {
  index: number
  docId: number
  docTitle: string
  docNumber?: string
  chunkIndex?: number
  chunkHeading?: string
  chunkText?: string
}

export interface RagSearchHit {
  chunkId: number
  docId: number
  spaceId: number
  chunkIndex: number
  chunkHeading?: string
  chunkText: string
  docTitle: string
  docNumber?: string
  score: number
}

export interface RagSearchResult {
  hits?: RagSearchHit[]
  effectiveSpaceIds?: number[]
  authorizedDocCount?: number
}

export interface RagSearchRequest {
  query: string
  spaceIds?: number[]
  topK?: number
}

export interface RagAskRequest {
  query: string
  spaceIds?: number[]
  topK?: number
  /** true = 思考型模型（慢、推理深），缺省 false = 快速模型 */
  deepThinking?: boolean
}

export interface RagAskUsage {
  promptTokens?: number
  completionTokens?: number
}

export interface RagAskDone {
  finishReason: string
  thinkingMs?: number
  answerMs?: number
  usage?: RagAskUsage | null
}

export interface RagAskMeta {
  effectiveSpaceIds?: number[]
  authorizedDocCount?: number
  citations?: RagCitation[]
}

export interface RagAskHandlers {
  onMeta?: (meta: RagAskMeta) => void
  onReason?: (text: string) => void
  onDelta?: (text: string) => void
  onDone?: (done: RagAskDone) => void
  onError?: (message: string) => void
}

/** 与后端 BaseResponse 对齐的本地类型（typings.d.ts 未覆盖 RAG 接口） */
export interface RagBaseResponse {
  code: number
  data?: RagSearchResult
  message?: string
}

/** 权限过滤检索（非流式） */
export async function ragSearchUsingPost(body: RagSearchRequest) {
  return request<RagBaseResponse>('/api/rag/search', {
    method: 'POST',
    data: body,
  })
}

// ---- 开放 API Key 管理（类型见 api/typings.d.ts 的 API.RagApiKeyView / API.RagApiKeyCreatedView）----

/** 创建 API Key（明文仅返回一次） */
export async function createRagApiKeyUsingPost(keyName: string) {
  return request<API.BaseResponseRagApiKeyCreated_>('/api/rag/key/create', {
    method: 'POST',
    data: { keyName },
  })
}

/** 列出当前用户的 API Key（不含明文） */
export async function listRagApiKeysUsingGet() {
  return request<API.BaseResponseListRagApiKeyView_>('/api/rag/key/list', {
    method: 'GET',
  })
}

/** 删除（吊销）API Key */
export async function deleteRagApiKeyUsingPost(id: number) {
  return request<API.BaseResponseBoolean_>('/api/rag/key/delete', {
    method: 'POST',
    data: { id },
  })
}

/**
 * SSE 流式问答。fetch + ReadableStream 解析 event/data 帧。
 * 业务失败（未登录等）在流建立前发生时走标准 BaseResponse，onError 回调。
 * @param signal AbortSignal，中断后 fetch 抛 AbortError（由调用方处理）
 */
export async function ragAskUsingStream(
  body: RagAskRequest,
  handlers: RagAskHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const base = (request.defaults.baseURL || '') as string
  const resp = await fetch(base + '/api/rag/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
    signal,
  })
  if (!resp.ok || !resp.body) {
    handlers.onError?.(`问答服务不可用（HTTP ${resp.status}）`)
    return
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // SSE 事件以空行分隔
    let sep: number
    while ((sep = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      handleFrame(frame, handlers)
    }
  }
}

function handleFrame(frame: string, handlers: RagAskHandlers) {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (dataLines.length === 0) return
  const raw = dataLines.join('\n')
  let payload: any = raw
  try {
    payload = JSON.parse(raw)
  } catch {
    // 纯文本 payload，保持原样
  }
  switch (eventName) {
    case 'meta':
      handlers.onMeta?.(payload as RagAskMeta)
      break
    case 'reason':
      handlers.onReason?.(String(payload.content ?? ''))
      break
    case 'delta':
      handlers.onDelta?.(String(payload.content ?? ''))
      break
    case 'done':
      handlers.onDone?.(payload as RagAskDone)
      break
    case 'error':
      handlers.onError?.(String(payload.message ?? '问答服务异常'))
      break
    default:
      break
  }
}
