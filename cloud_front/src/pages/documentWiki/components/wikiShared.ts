export const flattenFolderOptions = (
  folders: API.WikiFolderVis[],
  level = 0,
): { label: string; value: IdValue }[] =>
  folders.flatMap((folder) => [
    { label: `${'　'.repeat(level)}${folder.name}`, value: folder.id },
    ...flattenFolderOptions(folder.children ?? [], level + 1),
  ])

export const regionTitle = (space: API.WikiSpaceVis) => {
  if (space.type === 2) return '公开文档'
  if (space.type === 1) return '团队文档'
  return '个人文档'
}

export const formatTime = (time?: string) => {
  if (!time) return '-'
  const date = /^\d+$/.test(time) ? new Date(Number(time)) : new Date(time)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString()
}

export type IdValue = string | number | undefined
