import request from '@/request'

export interface LibraryCard {
  id: string
  name: string
  summary: string
  owner: boolean
  latestVersion: number
  version?: number
  state?: 'DRAFT' | 'ACTIVE' | 'DISABLED'
}

export interface SkillVersion {
  id: string
  version: number
  definitionJson: string
}

export interface CodeVersion {
  id: string
  version: number
  language: 'PYTHON' | 'JAVA'
  entrypoint: string
  sourceCode: string
  inputSchemaJson: string
  outputSchemaJson: string
  validationState: 'UNVERIFIED' | 'PASSED' | 'FAILED'
}

export interface CodeDraft {
  name: string
  summary: string
  language: 'PYTHON' | 'JAVA'
  entrypoint: string
  sourceCode: string
  inputSchemaJson: string
  outputSchemaJson: string
}

interface ApiResponse<T> { code: number; message?: string; data: T }
const base = '/api/agent/library'

export const listSkills = () => request<ApiResponse<LibraryCard[]>>(`${base}/skills`, { method: 'GET' })
export const listCodes = () => request<ApiResponse<LibraryCard[]>>(`${base}/codes`, { method: 'GET' })
export const getSkillVersion = (id: string, version: number) =>
  request<ApiResponse<SkillVersion>>(`${base}/skills/${encodeURIComponent(id)}/versions/${version}`, { method: 'GET' })
export const getCodeVersion = (id: string, version: number) =>
  request<ApiResponse<CodeVersion>>(`${base}/codes/${encodeURIComponent(id)}/versions/${version}`, { method: 'GET' })
export const createSkill = (definition: Record<string, unknown>) =>
  request<ApiResponse<string>>(`${base}/skills`, { method: 'POST', data: definition })
export const reviseSkill = (id: string, definition: Record<string, unknown>) =>
  request<ApiResponse<number>>(`${base}/skills/${encodeURIComponent(id)}/versions`, { method: 'POST', data: definition })
export const enableSkill = (id: string, version: number) =>
  request<ApiResponse<boolean>>(`${base}/skills/${encodeURIComponent(id)}/enable`, { method: 'POST', data: { version } })
export const disableSkill = (id: string) =>
  request<ApiResponse<boolean>>(`${base}/skills/${encodeURIComponent(id)}/disable`, { method: 'POST' })
export const shareSkill = (id: string, targetUserId: string) =>
  request<ApiResponse<boolean>>(`${base}/skills/${encodeURIComponent(id)}/share`, { method: 'POST', data: { targetUserId } })
export const createCode = (draft: CodeDraft) =>
  request<ApiResponse<string>>(`${base}/codes`, { method: 'POST', data: draft })
export const publishCode = (id: string, draft: CodeDraft) =>
  request<ApiResponse<number>>(`${base}/codes/${encodeURIComponent(id)}/versions`, { method: 'POST', data: draft })
export const shareCode = (id: string, targetUserId: string) =>
  request<ApiResponse<boolean>>(`${base}/codes/${encodeURIComponent(id)}/share`, { method: 'POST', data: { targetUserId } })
