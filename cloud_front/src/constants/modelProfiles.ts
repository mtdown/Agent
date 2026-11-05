/**
 * 模型选择器的模型清单（当前选择的单一事实源）。
 *
 * 只收录「生成 / 对话模型」：embedding 与 rerank 模型不能替换问答链路的 LLM，
 * 误选会造成零切片或重排报错，因此单列在 NON_CHAT_MODELS 中且不可选。
 *
 * 凭据契约：档位里只存「密钥变量名」apiKeyEnv，绝不存密钥值。明文由前端管理弹窗
 * 一次性 POST 给本地 dev server，写入项目根 .env.local（已被 gitignore）；
 * 后端 / Agent / Python 评测链都从环境变量或该文件读取。
 *
 * 自定义档位（用户在界面新增）不入库，只存浏览器 localStorage：
 * 见 MODEL_CUSTOM_STORAGE_KEY 与 isUsableCustomProfile 的读取校验。
 */

export type ModelLocation = 'local' | 'cloud'
export type ModelProvider = 'ollama' | 'deepseek' | 'dashscope' | 'other'

export interface ModelProfile {
  id: string
  label: string
  model: string
  baseUrl: string
  provider: ModelProvider
  location: ModelLocation
  group: string
  note: string
  /** 密钥变量名（引用，不是值）；空或缺省表示无需密钥。 */
  apiKeyEnv?: string
}

/** 用户在界面新增档位时填写的字段；id 与 group 由 store 生成。 */
export type CustomModelInput = Omit<ModelProfile, 'id' | 'group'>

export interface NonChatModel {
  label: string
  role: string
}

/** 浏览器本地保存选中档位的 key；后续评测链如需读取，用同一个 key。 */
export const MODEL_PROFILE_STORAGE_KEY = 'cloudwiki.selected-model-profile'

/** 用户自定义档位清单的 key（与选中档位分开，避免一份损坏影响另一份）。 */
export const MODEL_CUSTOM_STORAGE_KEY = 'cloudwiki.custom-model-profiles'

/** 自定义档位在下拉菜单中的分组名。 */
export const CUSTOM_MODEL_GROUP = '自定义模型'

/** 本地 .env.local 允许写入的键名，与 deploy/.env.example 及云端 GitHub Secrets 对齐。 */
export const LOCAL_ENV_KEYS: { value: string; label: string }[] = [
  { value: 'RAG_LLM_API_KEY', label: 'RAG_LLM_API_KEY · 回答生成（DeepSeek）' },
  { value: 'RAG_EMBEDDING_API_KEY', label: 'RAG_EMBEDDING_API_KEY · DashScope embedding / chat' },
  { value: 'DEEPSEEK_WEB_API_KEY', label: 'DEEPSEEK_WEB_API_KEY · Agent 联网' },
]

/** 档位表单可选：不需要密钥，或引用上表中的某个变量名。 */
export const API_KEY_ENV_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: '不需要密钥（本地 Ollama）' },
  ...LOCAL_ENV_KEYS,
]

export const LOCAL_ENV_KEY_NAMES: string[] = LOCAL_ENV_KEYS.map((item) => item.value)

/**
 * 写入端点只在 dev server 存在；生产构建把 DEV 折叠成 false，
 * 端点字符串随之被消除，云端产物里既没有端点也没有调用入口。
 */
export const LOCAL_ENV_ENDPOINT: string | null = import.meta.env.DEV ? '/__cw/local-env' : null
export const isLocalEnvWritable: boolean = LOCAL_ENV_ENDPOINT !== null

export const MODEL_PROVIDER_OPTIONS: { value: ModelProvider; label: string }[] = [
  { value: 'ollama', label: 'Ollama / 本地推理' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'dashscope', label: 'DashScope' },
  { value: 'other', label: '其他（OpenAI 兼容端点）' },
]

export const MODEL_LOCATION_OPTIONS: { value: ModelLocation; label: string }[] = [
  { value: 'local', label: '本地' },
  { value: 'cloud', label: '云端' },
]

/** 列表中只展示变量名，不展示任何密钥值。 */
export function describeCredential(profile: ModelProfile): string {
  return profile.apiKeyEnv ? `密钥变量 ${profile.apiKeyEnv}` : '无需密钥'
}

export const MODEL_PROFILES: ModelProfile[] = [
  {
    id: 'local-qwen3-1.7b',
    label: 'qwen3:1.7b',
    model: 'qwen3:1.7b',
    baseUrl: 'http://127.0.0.1:11434/v1',
    provider: 'ollama',
    location: 'local',
    group: '本地 · Ollama',
    note: '基础评测目标，本机已安装（completion / tools / thinking）',
  },
  {
    id: 'cloud-deepseek-flash',
    label: 'deepseek-flash',
    model: 'deepseek-flash',
    baseUrl: 'https://api.deepseek.com',
    provider: 'deepseek',
    location: 'cloud',
    group: '云端 · DeepSeek',
    note: 'RAG_LLM_FAST_MODEL 默认值；cloud_agent 当前使用',
    apiKeyEnv: 'RAG_LLM_API_KEY',
  },
  {
    id: 'cloud-deepseek-v4-flash-vision-exp',
    label: 'deepseek-v4-flash-vision-exp',
    model: 'deepseek-v4-flash-vision-exp',
    baseUrl: 'https://api.deepseek.com',
    provider: 'deepseek',
    location: 'cloud',
    group: '云端 · DeepSeek',
    note: 'RAG_LLM_MODEL 默认值（思考型）',
    apiKeyEnv: 'RAG_LLM_API_KEY',
  },
  {
    id: 'cloud-qwen3.8-flash',
    label: 'qwen3.8-flash',
    model: 'qwen3.8-flash',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    provider: 'dashscope',
    location: 'cloud',
    group: '云端 · DashScope',
    note: 'eval/.env 的 LLM_MODEL；RAG_MULTI_QUERY_MODEL 默认值',
    apiKeyEnv: 'RAG_EMBEDDING_API_KEY',
  },
  {
    id: 'cloud-qwen3.8-max',
    label: 'qwen3.8-max',
    model: 'qwen3.8-max',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    provider: 'dashscope',
    location: 'cloud',
    group: '云端 · DashScope',
    note: '历史答案层实验臂用过，非当前默认配置',
    apiKeyEnv: 'RAG_EMBEDDING_API_KEY',
  },
]

/** 检索链路专用模型：列出来便于核对现状，但不参与对话模型切换。 */
export const NON_CHAT_MODELS: NonChatModel[] = [
  { label: 'qwen3.7-text-embedding-flash', role: '云端 embedding' },
  { label: 'qwen3.7-text-rerank', role: '云端 rerank' },
  { label: 'qwen3-embedding:4b', role: '本地 embedding' },
  { label: 'nomic-embed-text:latest', role: '本地 embedding' },
]

/** 默认使用远程 DeepSeek Flash 档位。 */
export const DEFAULT_MODEL_PROFILE_ID = 'cloud-deepseek-flash'

export function getModelProfile(id: string): ModelProfile {
  return MODEL_PROFILES.find((profile) => profile.id === id)
    ?? MODEL_PROFILES.find((profile) => profile.id === DEFAULT_MODEL_PROFILE_ID)!
}

/**
 * localStorage 里的自定义档位是不可信输入：结构损坏、缺字段、id 与内置冲突
 * 都必须在读取时过滤掉，否则会让选择器白屏或覆盖内置档位。
 * apiKeyEnv 是可选字段：旧版本档位没有它时仍可用；一旦出现就必须是白名单里的
 * 变量名，杜绝把任意字符串（更不用说明文密钥）带进调用契约。
 */
export function isUsableCustomProfile(value: unknown): value is ModelProfile {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  const providers: string[] = MODEL_PROVIDER_OPTIONS.map((option) => option.value)
  const baseValid =
    typeof item.id === 'string' &&
    item.id.startsWith('custom-') &&
    typeof item.label === 'string' &&
    item.label.trim().length > 0 &&
    typeof item.model === 'string' &&
    item.model.trim().length > 0 &&
    typeof item.baseUrl === 'string' &&
    /^https?:\/\//i.test(item.baseUrl) &&
    (item.location === 'local' || item.location === 'cloud') &&
    typeof item.provider === 'string' &&
    providers.includes(item.provider) &&
    typeof item.note === 'string' &&
    typeof item.group === 'string'
  if (!baseValid) return false

  if (item.apiKeyEnv === undefined) return true
  if (typeof item.apiKeyEnv !== 'string') return false
  if (!item.apiKeyEnv) return true
  return LOCAL_ENV_KEY_NAMES.includes(item.apiKeyEnv)
}

export interface ModelProfileGroup {
  name: string
  profiles: ModelProfile[]
}

export const MODEL_PROFILE_GROUPS: ModelProfileGroup[] = MODEL_PROFILES.reduce<
  ModelProfileGroup[]
>((groups, profile) => {
  let group = groups.find((item) => item.name === profile.group)
  if (!group) {
    group = { name: profile.group, profiles: [] }
    groups.push(group)
  }
  group.profiles.push(profile)
  return groups
}, [])

/**
 * 只在本地显示，云端部署不渲染。
 * - 本地 dev（vite）：DEV=true，直接显示。
 * - 云端构建产物（vite build / preview）：DEV=false 且无该变量，组件不会渲染。
 * - 本地 preview 演示若也要显示，在 cloud_front/.env.local（*.local 已被 gitignore）写：
 *     VITE_ENABLE_MODEL_SELECTOR=true
 *   该文件不进版本库，所以云端构建永远拿不到它。
 * 注意：preview 下即使显示组件，写入端点也不存在，密钥区会自动禁用。
 */
export const isModelSelectorVisible: boolean =
  import.meta.env.DEV || import.meta.env.VITE_ENABLE_MODEL_SELECTOR === 'true'
