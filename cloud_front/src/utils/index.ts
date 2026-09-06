import { saveAs } from 'file-saver'

/**
 * 后端 Long/Snowflake 实体 id 的前端契约。
 * 保留 string 形式以避免大整数被压缩成不安全的 JavaScript number。
 */
export type EntityId = string | number

/**
 * 将路由 path/query 中的 id 规范化为实体 id 契约。
 * 仅做类型收窄，绝不使用 Number() 强转，防止 Snowflake id 精度丢失。
 */
export const normalizeRouteId = (value: unknown): EntityId | undefined => {
  if (typeof value === 'number') return value
  if (typeof value === 'string' && value !== '') return value
  if (Array.isArray(value)) return normalizeRouteId(value[0])
  return undefined
}

export const formatSize = (size?: number | string) => {
  const num = Number(size)
  if (!num) return '未知'
  if (num < 1024) return num + ' B'
  if (num < 1024 * 1024) return (num / 1024).toFixed(2) + ' KB'
  return (num / (1024 * 1024)).toFixed(2) + ' MB'
}

export function downloadImage(url?: string, fileName?: string) {
  if (!url) {
    return
  }
  saveAs(url, fileName)
}
