import type { RagCitation } from '@/api/ragController'

/** Older Agent snapshots stored Java long IDs as JSON numbers. Quote them before JSON.parse. */
export function parseCitationSnapshot(value?: string | null): RagCitation[] {
  if (!value) return []
  try {
    const exactIds = value.replace(/("docId"\s*:\s*)(\d+)(?=\s*[,}])/g, '$1"$2"')
    const citations: unknown = JSON.parse(exactIds)
    return Array.isArray(citations) ? citations as RagCitation[] : []
  } catch {
    return []
  }
}
