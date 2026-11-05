import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  CUSTOM_MODEL_GROUP,
  DEFAULT_MODEL_PROFILE_ID,
  MODEL_CUSTOM_STORAGE_KEY,
  MODEL_PROFILE_STORAGE_KEY,
  MODEL_PROFILES,
  getModelProfile,
  isUsableCustomProfile,
  type CustomModelInput,
  type ModelProfile,
} from '@/constants/modelProfiles.ts'

/** 写 localStorage 失败（隐私模式 / 配额满）时不影响内存态。 */
function persist(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // 内存中的选择仍然有效。
  }
}

/**
 * 自定义档位：不可信输入，逐项校验并排除与内置档位 id 冲突的项。
 * 顺带清掉历史上可能写入的 apiKeyMode / apiKeyRef（含明文模式），
 * 保证浏览器存储里永远只剩变量名引用，不残留任何密钥值。
 */
function readCustomProfiles(): ModelProfile[] {
  try {
    const raw = window.localStorage.getItem(MODEL_CUSTOM_STORAGE_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    const cleaned = parsed
      .filter(isUsableCustomProfile)
      .filter((profile) => !MODEL_PROFILES.some((builtin) => builtin.id === profile.id))
      .map((profile) => {
        const item = { ...profile } as Record<string, unknown>
        delete item.apiKeyMode
        delete item.apiKeyRef
        return item as unknown as ModelProfile
      })
    persist(MODEL_CUSTOM_STORAGE_KEY, JSON.stringify(cleaned))
    return cleaned
  } catch {
    return []
  }
}

/** 读回上次选择；无效 id（含已删除的自定义项）或存储不可用时回落到默认档位。 */
function readStoredId(custom: ModelProfile[]): string {
  try {
    const stored = window.localStorage.getItem(MODEL_PROFILE_STORAGE_KEY)
    if (!stored) return DEFAULT_MODEL_PROFILE_ID
    return [...MODEL_PROFILES, ...custom].some((profile) => profile.id === stored)
      ? stored
      : DEFAULT_MODEL_PROFILE_ID
  } catch {
    return DEFAULT_MODEL_PROFILE_ID
  }
}

/**
 * 当前选中的模型档位：内置清单 + 用户自定义清单的唯一事实源。
 * 任何组件（含后续评测入口）都可以用 useModelProfileStore().selected 读到完整的
 * baseUrl / model / provider，不需要再各自维护一份模型表。
 *
 * 注意：自定义档位只存在本浏览器的 localStorage，Python 评测链读不到；
 * 若要让评测链使用自定义档位，需另行把该档位同步到评测侧配置（本次未实现）。
 */
export const useModelProfileStore = defineStore('modelProfile', () => {
  const customProfiles = ref<ModelProfile[]>(readCustomProfiles())
  const allProfiles = computed<ModelProfile[]>(() => [
    ...MODEL_PROFILES,
    ...customProfiles.value,
  ])
  const selectedId = ref<string>(readStoredId(customProfiles.value))
  const selected = computed<ModelProfile>(
    () => allProfiles.value.find((profile) => profile.id === selectedId.value) ?? getModelProfile(DEFAULT_MODEL_PROFILE_ID),
  )

  function setSelectedId(id: string) {
    const profile = allProfiles.value.find((item) => item.id === id)
    if (!profile) return
    selectedId.value = profile.id
    persist(MODEL_PROFILE_STORAGE_KEY, profile.id)
  }

  /** 新增自定义档位，id 带时间戳 + 随机后缀，避免与内置或历史项冲突。 */
  function addCustomProfile(input: CustomModelInput): ModelProfile {
    const profile: ModelProfile = {
      ...input,
      id: `custom-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
      group: CUSTOM_MODEL_GROUP,
    }
    customProfiles.value = [...customProfiles.value, profile]
    persist(MODEL_CUSTOM_STORAGE_KEY, JSON.stringify(customProfiles.value))
    return profile
  }

  /** 删除自定义档位；若正选中它则回落到默认档位。内置档位不可删。 */
  function removeCustomProfile(id: string) {
    const next = customProfiles.value.filter((profile) => profile.id !== id)
    if (next.length === customProfiles.value.length) return
    customProfiles.value = next
    persist(MODEL_CUSTOM_STORAGE_KEY, JSON.stringify(next))
    if (selectedId.value === id) setSelectedId(DEFAULT_MODEL_PROFILE_ID)
  }

  return {
    selectedId,
    selected,
    customProfiles,
    allProfiles,
    setSelectedId,
    addCustomProfile,
    removeCustomProfile,
  }
})
