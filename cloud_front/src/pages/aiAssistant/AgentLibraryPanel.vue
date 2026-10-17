<template>
  <section class="library-panel">
    <header class="library-header">
      <div>
        <div class="library-eyebrow">Agent 资源</div>
        <h2>{{ mode === 'skill' ? 'Skill 库' : '代码库' }}</h2>
        <p>{{ mode === 'skill' ? '查看可复用的技能流程与版本。' : '编辑、发布并追溯 Python 或 Java 代码。' }}</p>
      </div>
      <div class="library-actions">
        <a-button @click="$emit('back')">返回聊天</a-button>
        <a-button type="primary" @click="createNew">{{ mode === 'skill' ? '新建 Skill 草稿' : '新建代码' }}</a-button>
      </div>
    </header>

    <a-spin :spinning="loading">
      <div v-if="cards.length" class="library-cards">
        <button v-for="card in cards" :key="card.id" class="library-card" type="button" @click="openCard(card)">
          <span class="card-name">{{ card.name }}</span>
          <span class="card-summary">{{ card.summary }}</span>
          <span class="card-meta">
            <a-tag v-if="mode === 'skill'" :color="card.state === 'ACTIVE' ? 'green' : 'default'">{{ stateText(card.state) }}</a-tag>
            <a-tag v-else color="blue">代码</a-tag>
            <span>v{{ card.latestVersion }}</span>
            <span>{{ card.owner ? '我的' : '已共享' }}</span>
          </span>
        </button>
      </div>
      <div v-else-if="!loading" class="library-empty">
        {{ mode === 'skill' ? '还没有 Skill。完成任务后，Agent 可以为你生成草稿。' : '还没有代码。可以在线创建并发布第一个版本。' }}
      </div>
    </a-spin>

    <a-drawer v-model:open="drawerOpen" :width="720" :title="drawerTitle" :destroy-on-close="false">
      <div class="editor-topline">
        <span v-if="selected">资源位置：{{ mode === 'code' ? `code:${selected.id}@${shownVersion}#${codeDraft.entrypoint}` : `skill:${selected.id}@${shownVersion}` }}</span>
        <span v-else>创建后将获得固定的资源 ID 和版本。</span>
      </div>
      <div v-if="selected" class="version-bar">
        <span>查看版本</span>
        <a-input-number v-model:value="requestedVersion" :min="1" :max="selected.latestVersion" size="small" />
        <a-button size="small" :loading="detailLoading" @click="loadVersion">打开</a-button>
      </div>

      <template v-if="mode === 'skill'">
        <a-alert v-if="selected?.state === 'DRAFT'" type="info" show-icon message="草稿不会被 Agent 自动选择或执行。" class="editor-alert" />
        <div class="field-label">Skill 定义（JSON）</div>
        <a-textarea v-model:value="skillJson" class="source-editor" :rows="22" :disabled="!!selected && !selected.owner" spellcheck="false" />
        <div class="editor-help">包含名称、能力描述、适用条件、参数、步骤、预期结果、失败策略和样例。代码步骤需引用已验证的固定代码版本。</div>
        <div v-if="!selected || selected.owner" class="editor-buttons">
          <a-button type="primary" :loading="saving" @click="saveSkill">{{ selected ? '发布新版本' : '保存草稿' }}</a-button>
          <a-button v-if="selected" :loading="saving" @click="toggleSkill">{{ selected.state === 'ACTIVE' && selected.version === shownVersion ? '停用' : `启用 v${shownVersion}` }}</a-button>
        </div>
      </template>

      <template v-else>
        <div class="form-grid">
          <label>代码名称<a-input v-model:value="codeDraft.name" :disabled="!!selected" :maxlength="160" /></label>
          <label>能力描述<a-input v-model:value="codeDraft.summary" :disabled="!!selected" :maxlength="500" /></label>
          <label>语言<a-select v-model:value="codeDraft.language" :disabled="!!selected && !selected.owner" :options="languageOptions" /></label>
          <label>入口<a-input v-model:value="codeDraft.entrypoint" :disabled="!!selected && !selected.owner" /></label>
        </div>
        <div class="field-label">源代码</div>
        <a-textarea v-model:value="codeDraft.sourceCode" class="source-editor" :rows="15" :disabled="!!selected && !selected.owner" spellcheck="false" />
        <div class="field-label">输入结构（JSON Schema）</div>
        <a-textarea v-model:value="codeDraft.inputSchemaJson" :rows="4" :disabled="!!selected && !selected.owner" spellcheck="false" />
        <div class="field-label">输出结构（JSON Schema）</div>
        <a-textarea v-model:value="codeDraft.outputSchemaJson" :rows="4" :disabled="!!selected && !selected.owner" spellcheck="false" />
        <a-alert v-if="selected" class="editor-alert" type="info" show-icon :message="`当前版本样例状态：${validationText}`" />
        <div v-if="!selected || selected.owner" class="editor-buttons">
          <a-button type="primary" :loading="saving" @click="saveCode">{{ selected ? '发布新版本' : '创建并发布' }}</a-button>
        </div>
      </template>

      <div v-if="selected?.owner" class="share-bar">
        <a-input v-model:value="shareTarget" placeholder="共享给用户 ID（只读和使用）" />
        <a-button :loading="saving" @click="shareSelected">共享</a-button>
      </div>
    </a-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  listSkills, listCodes, getSkillVersion, getCodeVersion, createSkill, reviseSkill,
  enableSkill, disableSkill, shareSkill, createCode, publishCode, shareCode,
  type LibraryCard, type CodeDraft,
} from '@/api/agentLibrary'

const props = defineProps<{ mode: 'skill' | 'code' }>()
defineEmits<{ back: [] }>()
const languageOptions = [{ label: 'Python', value: 'PYTHON' }, { label: 'Java', value: 'JAVA' }]
const cards = ref<LibraryCard[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const drawerOpen = ref(false)
const selected = ref<LibraryCard | null>(null)
const shownVersion = ref(1)
const requestedVersion = ref<number | null>(1)
const skillJson = ref('')
const validationState = ref('UNVERIFIED')
const shareTarget = ref('')
const codeDraft = reactive<CodeDraft>({
  name: '', summary: '', language: 'PYTHON', entrypoint: 'run', sourceCode: '',
  inputSchemaJson: '{"type":"object","properties":{}}',
  outputSchemaJson: '{"type":"object","properties":{}}',
})

const drawerTitle = computed(() => `${props.mode === 'skill' ? 'Skill' : '代码'} · ${selected.value?.name ?? '新建'}`)
const validationText = computed(() => ({ PASSED: '已通过', FAILED: '失败', UNVERIFIED: '待验证' })[validationState.value as 'PASSED' | 'FAILED' | 'UNVERIFIED'] ?? '待验证')
const stateText = (state?: string) => ({ ACTIVE: '已启用', DRAFT: '草稿', DISABLED: '已停用' })[state as 'ACTIVE' | 'DRAFT' | 'DISABLED'] ?? '草稿'

function unwrap<T>(response: { data: { code: number; message?: string; data: T } }): T {
  if (response.data.code !== 0) throw new Error(response.data.message || '请求失败')
  return response.data.data
}

async function refresh() {
  loading.value = true
  try { cards.value = unwrap(props.mode === 'skill' ? await listSkills() : await listCodes()) ?? [] }
  catch (error) { message.error(error instanceof Error ? error.message : '加载资源库失败') }
  finally { loading.value = false }
}
watch(() => props.mode, () => { drawerOpen.value = false; selected.value = null; void refresh() }, { immediate: true })

function createNew() {
  selected.value = null
  shownVersion.value = 1
  requestedVersion.value = 1
  shareTarget.value = ''
  skillJson.value = JSON.stringify({
    name: '新 Skill', summary: '一句话描述能力', when_to_use: '适用条件', when_not_to_use: '不适用条件',
    parameters: [], steps: [{ type: 'response', template: '在这里描述输出' }],
    expected_results: ['可核验的结果'], failure_policy: 'stop', examples: [{ input: {}, expected: {} }],
  }, null, 2)
  Object.assign(codeDraft, { name: '', summary: '', language: 'PYTHON', entrypoint: 'run', sourceCode: '',
    inputSchemaJson: '{"type":"object","properties":{}}', outputSchemaJson: '{"type":"object","properties":{}}' })
  drawerOpen.value = true
}

async function openCard(card: LibraryCard) {
  selected.value = card
  requestedVersion.value = card.latestVersion
  shareTarget.value = ''
  drawerOpen.value = true
  await loadVersion()
}

async function loadVersion() {
  if (!selected.value || !requestedVersion.value) return
  detailLoading.value = true
  try {
    if (props.mode === 'skill') {
      const version = unwrap(await getSkillVersion(selected.value.id, requestedVersion.value))
      skillJson.value = JSON.stringify(JSON.parse(version.definitionJson), null, 2)
      shownVersion.value = version.version
    } else {
      const version = unwrap(await getCodeVersion(selected.value.id, requestedVersion.value))
      Object.assign(codeDraft, {
        name: selected.value.name, summary: selected.value.summary, language: version.language,
        entrypoint: version.entrypoint, sourceCode: version.sourceCode,
        inputSchemaJson: version.inputSchemaJson, outputSchemaJson: version.outputSchemaJson,
      })
      validationState.value = version.validationState
      shownVersion.value = version.version
    }
  } catch (error) { message.error(error instanceof Error ? error.message : '读取版本失败') }
  finally { detailLoading.value = false }
}

async function saveSkill() {
  if (selected.value && !selected.value.owner) return
  let definition: Record<string, unknown>
  try {
    definition = JSON.parse(skillJson.value) as Record<string, unknown>
    if (!definition || Array.isArray(definition) || typeof definition !== 'object') throw new Error('Skill 必须是 JSON 对象')
  } catch { message.error('请输入有效的 Skill JSON 对象'); return }
  saving.value = true
  try {
    if (selected.value) {
      const version = unwrap(await reviseSkill(selected.value.id, definition))
      shownVersion.value = version
      requestedVersion.value = version
      selected.value.latestVersion = version
      message.success(`已发布 Skill v${version}`)
    } else {
      unwrap(await createSkill(definition))
      message.success('Skill 草稿已保存，请审阅后启用')
      drawerOpen.value = false
    }
    await refresh()
  } catch (error) { message.error(error instanceof Error ? error.message : '保存 Skill 失败') }
  finally { saving.value = false }
}

async function toggleSkill() {
  if (!selected.value?.owner) return
  saving.value = true
  try {
    if (selected.value.state === 'ACTIVE' && selected.value.version === shownVersion.value)
      unwrap(await disableSkill(selected.value.id))
    else unwrap(await enableSkill(selected.value.id, shownVersion.value))
    drawerOpen.value = false
    await refresh()
    message.success('状态已更新')
  } catch (error) { message.error(error instanceof Error ? error.message : '更新状态失败') }
  finally { saving.value = false }
}

async function saveCode() {
  if (selected.value && !selected.value.owner) return
  saving.value = true
  try {
    if (selected.value) {
      const version = unwrap(await publishCode(selected.value.id, codeDraft))
      shownVersion.value = version
      requestedVersion.value = version
      selected.value.latestVersion = version
      validationState.value = 'UNVERIFIED'
      message.success(`已发布代码 v${version}`)
    } else {
      unwrap(await createCode(codeDraft))
      message.success('代码已创建并发布，样例验证后可绑定 Skill')
      drawerOpen.value = false
    }
    await refresh()
  } catch (error) { message.error(error instanceof Error ? error.message : '发布代码失败') }
  finally { saving.value = false }
}

async function shareSelected() {
  if (!selected.value?.owner || !/^[1-9]\d{0,18}$/.test(shareTarget.value)) {
    message.warning('请输入有效的用户 ID')
    return
  }
  saving.value = true
  try {
    if (props.mode === 'skill') unwrap(await shareSkill(selected.value.id, shareTarget.value))
    else unwrap(await shareCode(selected.value.id, shareTarget.value))
    shareTarget.value = ''
    message.success('共享已保存')
  } catch (error) { message.error(error instanceof Error ? error.message : '共享失败') }
  finally { saving.value = false }
}
</script>

<style scoped>
.library-panel { flex: 1; min-width: 0; min-height: 480px; height: calc(100vh - 190px); overflow-y: auto; padding: 24px; background: #fffdf8; border: 1px solid #e8dcc8; border-radius: 12px; box-shadow: 0 2px 8px rgba(120,90,40,.08); }
.library-header { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; margin-bottom: 24px; }
.library-eyebrow { color: #a08355; font-size: 12px; letter-spacing: .08em; }
.library-header h2 { margin: 4px 0; font-size: 24px; color: #3f3425; }
.library-header p { margin: 0; color: #82745f; }
.library-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.library-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 14px; }
.library-card { display: flex; flex-direction: column; gap: 10px; text-align: left; background: #fff; border: 1px solid #e8dcc8; border-radius: 12px; padding: 18px; min-height: 150px; cursor: pointer; transition: border-color .18s, transform .18s; }
.library-card:hover { border-color: #bb945a; transform: translateY(-2px); }
.card-name { color: #392d1d; font-weight: 600; font-size: 16px; }
.card-summary { color: #786d5e; font-size: 13px; line-height: 1.5; flex: 1; overflow-wrap: anywhere; }
.card-meta { display: flex; align-items: center; gap: 7px; color: #92836d; font-size: 12px; }
.library-empty { padding: 80px 20px; text-align: center; color: #92836d; border: 1px dashed #dbc9ac; border-radius: 12px; }
.editor-topline { color: #88765d; font-size: 12px; overflow-wrap: anywhere; margin-bottom: 14px; }
.version-bar { display: flex; align-items: center; gap: 9px; margin-bottom: 20px; }
.field-label { margin: 16px 0 6px; font-weight: 600; color: #514331; }
.source-editor { font-family: Consolas, 'Courier New', monospace; }
.editor-help { color: #88765d; font-size: 12px; line-height: 1.5; margin-top: 7px; }
.editor-buttons { display: flex; gap: 8px; margin-top: 18px; }
.editor-alert { margin: 12px 0; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.form-grid label { display: flex; flex-direction: column; gap: 6px; color: #514331; font-weight: 600; }
.share-bar { border-top: 1px solid #eee2cf; padding-top: 18px; margin-top: 24px; display: flex; gap: 8px; }
@media (max-width: 700px) { .library-panel { height: auto; min-height: 70vh; } .library-header { flex-direction: column; } .form-grid { grid-template-columns: 1fr; } }
</style>
