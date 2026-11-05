<template>
  <div v-if="visible" class="model-selector">
    <a-dropdown :trigger="['click']" placement="topLeft">
      <button type="button" class="model-selector-trigger" :title="SELECTOR_HINT">
        <span class="model-selector-name">{{ selected.label }}</span>
        <span class="model-selector-location">
          {{ selected.location === 'local' ? '本地' : '云端' }}
        </span>
        <DownOutlined class="model-selector-chevron" />
      </button>
      <template #overlay>
        <a-menu :selectedKeys="[selectedId]" @click="onSelect">
          <a-menu-item-group
            v-for="group in groups"
            :key="group.name"
            :title="group.name"
          >
            <a-menu-item v-for="profile in group.profiles" :key="profile.id">
              <div class="model-option">
                <span class="model-option-name">{{ profile.label }}</span>
                <span class="model-option-note">{{ profile.note }}</span>
              </div>
            </a-menu-item>
          </a-menu-item-group>
          <a-menu-divider />
          <a-menu-item :key="MANAGE_KEY">
            <span class="model-manage-entry">
              <PlusOutlined /> 新增 / 管理模型
            </span>
          </a-menu-item>
          <a-menu-divider />
          <a-menu-item-group title="检索链路专用 · 不可选">
            <a-menu-item v-for="item in NON_CHAT_MODELS" :key="item.label" disabled>
              <div class="model-option">
                <span class="model-option-name">{{ item.label }}</span>
                <span class="model-option-note">{{ item.role }}</span>
              </div>
            </a-menu-item>
          </a-menu-item-group>
          <a-menu-divider />
          <a-menu-item disabled>
            <div class="model-hint">{{ SELECTOR_HINT }}</div>
            <div class="model-hint">{{ CUSTOM_HINT }}</div>
          </a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>

    <a-modal
      v-model:open="managerOpen"
      title="自定义模型与本地密钥"
      :footer="null"
      width="560px"
    >
      <div class="model-manager">
        <div class="manager-section-title">新增档位</div>
        <div class="manager-form">
          <label class="manager-field">
            <span>显示名称</span>
            <a-input v-model:value="form.label" placeholder="如 qwen3:8b" allow-clear />
          </label>
          <label class="manager-field">
            <span>模型 ID</span>
            <a-input v-model:value="form.model" placeholder="调用时传给 API 的 model 值" allow-clear />
          </label>
          <label class="manager-field">
            <span>Base URL</span>
            <a-input
              v-model:value="form.baseUrl"
              placeholder="http://127.0.0.1:11434/v1"
              allow-clear
            />
          </label>
          <div class="manager-row">
            <label class="manager-field">
              <span>位置</span>
              <a-select v-model:value="form.location" :options="MODEL_LOCATION_OPTIONS" />
            </label>
            <label class="manager-field">
              <span>提供商</span>
              <a-select v-model:value="form.provider" :options="MODEL_PROVIDER_OPTIONS" />
            </label>
          </div>
          <label class="manager-field">
            <span>备注（选填）</span>
            <a-input v-model:value="form.note" placeholder="用途说明" allow-clear />
          </label>
          <label class="manager-field">
            <span>密钥变量名（选填，只存引用不存值）</span>
            <a-select v-model:value="form.apiKeyEnv" :options="API_KEY_ENV_OPTIONS" />
          </label>
          <label v-if="form.apiKeyEnv" class="manager-field">
            <span>API Key（选填，写入项目根 .env.local）</span>
            <a-input-password
              v-model:value="newModelKey"
              :disabled="!envWritable"
              placeholder="留空则只引用已配置的密钥"
              autocomplete="off"
            />
          </label>
          <a-button type="primary" block :loading="submitting" @click="submitCustom">
            添加到列表并选中
          </a-button>
        </div>

        <div class="manager-section-title">
          已添加的自定义模型（{{ customProfiles.length }}）
        </div>
        <div v-if="!customProfiles.length" class="manager-empty">
          暂无。新增后会出现在下拉菜单的「自定义模型」分组。
        </div>
        <div v-for="item in customProfiles" :key="item.id" class="manager-item">
          <div class="manager-item-info">
            <span class="manager-item-name">{{ item.label }}</span>
            <span class="manager-item-meta">
              {{ item.location === 'local' ? '本地' : '云端' }} · {{ item.model }} ·
              {{ item.baseUrl }}
            </span>
            <span class="manager-item-meta">{{ describeCredential(item) }}</span>
          </div>
          <a-popconfirm
            title="删除这个自定义档位？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="onRemove(item.id)"
          >
            <a-button danger size="small" type="text">删除</a-button>
          </a-popconfirm>
        </div>

        <div class="manager-section-title">本地密钥（项目根 .env.local）</div>
        <div v-if="!envWritable" class="manager-empty">
          密钥写入端点不可用；云端构建或启用临时隧道时不提供该能力。
        </div>
        <template v-else>
          <div v-for="item in LOCAL_ENV_KEYS" :key="item.value" class="env-status-row">
            <span class="env-status-name">{{ item.value }}</span>
            <a-tag :color="envStatus[item.value] ? 'green' : 'default'">
              {{ envStatus[item.value] ? '文件内已配置' : '文件内未配置' }}
            </a-tag>
          </div>
          <div class="manager-row env-write-row">
            <label class="manager-field">
              <span>变量名</span>
              <a-select v-model:value="keyForm.envKey" :options="LOCAL_ENV_KEYS" />
            </label>
            <label class="manager-field">
              <span>密钥值</span>
              <a-input-password
                v-model:value="keyForm.value"
                placeholder="sk-..."
                autocomplete="off"
              />
            </label>
          </div>
          <a-button block :loading="savingKey" @click="saveStandaloneKey">
            保存到 .env.local
          </a-button>
          <p class="manager-hint">
            状态只反映 .env.local 文件本身；系统环境变量里的同名键仍会生效。
            明文只发给本机 dev server，不保存在浏览器、不进入前端产物；
            写入后需重启后端与 Agent 才会生效。
          </p>
        </template>

        <p class="manager-hint">{{ CUSTOM_HINT }}</p>
        <p class="manager-hint">{{ SELECTOR_HINT }}</p>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { DownOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { type MenuProps, message } from 'ant-design-vue'
import {
  API_KEY_ENV_OPTIONS,
  CUSTOM_MODEL_GROUP,
  LOCAL_ENV_ENDPOINT,
  LOCAL_ENV_KEYS,
  describeCredential,
  isLocalEnvWritable,
  isModelSelectorVisible,
  MODEL_LOCATION_OPTIONS,
  MODEL_PROFILE_GROUPS,
  MODEL_PROVIDER_OPTIONS,
  NON_CHAT_MODELS,
  type CustomModelInput,
  type ModelProfileGroup,
} from '@/constants/modelProfiles.ts'
import { useModelProfileStore } from '@/stores/useModelProfileStore.ts'

const MANAGE_KEY = '__manage-model__'
const SELECTOR_HINT =
  '选择仅保存在本地浏览器，供后续评测链读取；后端模型仍由启动环境变量决定，尚未随此按钮切换。'
const CUSTOM_HINT =
  '自定义档位只存本浏览器（Python 评测链读不到）；密钥值不存浏览器，统一写入项目根 .env.local。'

interface LocalEnvResponse {
  ok: boolean
  keys?: { key: string; configured: boolean }[]
  error?: string
}

const visible = isModelSelectorVisible
const store = useModelProfileStore()
const selected = computed(() => store.selected)
const selectedId = computed(() => store.selectedId)
const customProfiles = computed(() => store.customProfiles)

const groups = computed<ModelProfileGroup[]>(() => {
  const result = MODEL_PROFILE_GROUPS.map((group) => ({
    ...group,
    profiles: [...group.profiles],
  }))
  if (customProfiles.value.length) {
    result.push({ name: CUSTOM_MODEL_GROUP, profiles: customProfiles.value })
  }
  return result
})

const managerOpen = ref(false)
const form = reactive<CustomModelInput>({
  label: '',
  model: '',
  baseUrl: '',
  provider: 'ollama',
  location: 'local',
  note: '',
  apiKeyEnv: '',
})
const newModelKey = ref('')
const submitting = ref(false)

const envWritable = ref(false)
const envStatus = ref<Record<string, boolean>>({})
const keyForm = reactive({ envKey: LOCAL_ENV_KEYS[0].value, value: '' })
const savingKey = ref(false)

/** dev server 的本地端点：GET 只取「是否配置」布尔，POST 只发明文值。 */
async function callLocalEnv(
  method: 'GET' | 'POST',
  payload?: { key: string; value: string },
): Promise<LocalEnvResponse> {
  if (!LOCAL_ENV_ENDPOINT) throw new Error('当前构建不提供本地密钥端点')
  const response = await fetch(LOCAL_ENV_ENDPOINT, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: method === 'POST' ? JSON.stringify(payload) : undefined,
  })
  let data: LocalEnvResponse
  try {
    data = (await response.json()) as LocalEnvResponse
  } catch {
    throw new Error(`HTTP ${response.status}`)
  }
  if (!response.ok || !data.ok) throw new Error(data.error ?? `HTTP ${response.status}`)
  return data
}

async function refreshEnvStatus() {
  if (!isLocalEnvWritable) return
  try {
    const data = await callLocalEnv('GET')
    envStatus.value = Object.fromEntries(
      (data.keys ?? []).map((item) => [item.key, item.configured]),
    )
    envWritable.value = true
  } catch {
    // 端点不可达（例如本地 preview 强行显示了组件）时禁用密钥区，不影响档位选择。
    envWritable.value = false
  }
}

/** 返回是否写入成功；明文在请求结束后即清空，不进入任何持久化层。 */
async function saveEnvKey(key: string, value: string): Promise<boolean> {
  if (!envWritable.value) {
    message.warning('密钥写入端点不可用')
    return false
  }
  savingKey.value = true
  try {
    await callLocalEnv('POST', { key, value })
    await refreshEnvStatus()
    message.success(`已写入 .env.local：${key}；重启后端与 Agent 后生效`)
    return true
  } catch (error) {
    message.error(`密钥保存失败：${(error as Error).message}`)
    return false
  } finally {
    savingKey.value = false
  }
}

async function saveStandaloneKey() {
  const value = keyForm.value.trim()
  if (!value) {
    message.warning('请填写密钥值')
    return
  }
  const ok = await saveEnvKey(keyForm.envKey, value)
  if (ok) keyForm.value = ''
}

function resetForm() {
  Object.assign(form, {
    label: '',
    model: '',
    baseUrl: '',
    provider: 'ollama',
    location: 'local',
    note: '',
    apiKeyEnv: '',
  } satisfies CustomModelInput)
  newModelKey.value = ''
}

const onSelect: MenuProps['onClick'] = ({ key }) => {
  const id = String(key)
  if (id === MANAGE_KEY) {
    managerOpen.value = true
    return
  }
  // store 内部按 allProfiles 校验 id，非法 key（含禁用项）会被忽略。
  store.setSelectedId(id)
}

async function submitCustom() {
  if (submitting.value) return
  const label = form.label.trim()
  const model = form.model.trim()
  const baseUrl = form.baseUrl.trim().replace(/\/+$/, '')
  if (!label || !model || !baseUrl) {
    message.warning('请填写显示名称、模型 ID 与 Base URL')
    return
  }
  if (!/^https?:\/\//i.test(baseUrl)) {
    message.warning('Base URL 需以 http:// 或 https:// 开头')
    return
  }
  try {
    new URL(baseUrl)
  } catch {
    message.warning('Base URL 格式不正确')
    return
  }

  submitting.value = true
  try {
    // 填了 API Key 就先落盘，写入失败则中止新增，避免出现「档位引用了不存在的密钥」。
    if (form.apiKeyEnv && newModelKey.value.trim()) {
      const ok = await saveEnvKey(form.apiKeyEnv, newModelKey.value.trim())
      if (!ok) return
    }
    const profile = store.addCustomProfile({
      label,
      model,
      baseUrl,
      provider: form.provider,
      location: form.location,
      note: form.note.trim() || '自定义档位（仅本浏览器）',
      apiKeyEnv: form.apiKeyEnv || undefined,
    })
    store.setSelectedId(profile.id)
    message.success(`已添加并选中 ${label}`)
    resetForm()
    await refreshEnvStatus()
  } finally {
    submitting.value = false
  }
}

function onRemove(id: string) {
  store.removeCustomProfile(id)
  message.success('已删除')
}

onMounted(() => {
  void refreshEnvStatus()
})
watch(managerOpen, (open) => {
  if (open) void refreshEnvStatus()
})
</script>

<style scoped>
/*
 * 紧凑文字触发器：贴合对话窗底部工具条的视觉语言 —— 无边框、无底色，
 * 只保留模型名 + 位置小字 + 下拉箭头。浅色文案适配 .input-bar 的米色背景。
 */
.model-selector {
  flex: 0 1 auto;
  min-width: 0;
}

.model-selector-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  height: 28px;
  padding: 0 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #6b5b3e;
  font-family: inherit;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.model-selector-trigger:hover {
  background: #f0e5d2;
  color: #3d3226;
}

.model-selector-trigger:focus-visible {
  outline: 2px solid #e07a2d;
  outline-offset: 1px;
}

.model-selector-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-selector-location {
  flex: 0 0 auto;
  color: #a08c68;
  font-size: 12px;
}

.model-selector-chevron {
  flex: 0 0 auto;
  color: #a08c68;
  font-size: 10px;
}

.model-manage-entry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #a14f16;
  font-size: 13px;
}

.model-hint {
  max-width: 300px;
  white-space: normal;
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
}

.model-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-width: 320px;
  padding: 2px 0;
}

.model-option-name {
  font-size: 13px;
  line-height: 1.4;
}

.model-option-note {
  font-size: 12px;
  line-height: 1.4;
  color: #8c8c8c;
}

/* 自定义模型管理弹窗 */
.model-manager {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}

.manager-section-title {
  margin: 4px 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: #3d3226;
}

.manager-section-title:not(:first-child) {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid #efe4d0;
}

.manager-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.manager-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #6b5b3e;
}

.manager-row {
  display: flex;
  gap: 10px;
}

.manager-empty {
  padding: 10px 0;
  font-size: 12px;
  line-height: 1.6;
  color: #a08c68;
}

.manager-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border: 1px solid #efe4d0;
  border-radius: 6px;
  background: #fdf9f1;
}

.manager-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.manager-item-name {
  font-size: 13px;
  color: #3d3226;
}

.manager-item-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: #a08c68;
}

.env-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 2px;
  border-bottom: 1px solid #f3ead9;
  font-size: 12px;
  color: #6b5b3e;
}

.env-status-name {
  font-family: Consolas, 'Courier New', monospace;
}

.env-write-row {
  margin: 12px 0;
}

.manager-hint {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #8c8c8c;
}
</style>
