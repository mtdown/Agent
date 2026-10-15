export interface SpaceOption {
  label: string
  value: string
  type: number
  folders?: FolderOption[]
}

export interface FolderOption {
  id: string
  label: string
  children: FolderOption[]
}

export interface FlatFolder extends FolderOption {
  depth: number
  descendantIds: string[]
  ancestorIds: string[]
}

export interface SpaceGroup {
  type: number
  label: string
  spaces: SpaceOption[]
}

export interface SpaceSelection {
  allSpaces: boolean
  selectedIds: string[]
  folderIds?: string[]
}

interface VisibleSpace {
  id?: string | number
  name?: string
  type?: number
}

interface VisibleFolder {
  id?: string | number
  name?: string
  children?: VisibleFolder[]
}

const GROUPS = [
  { type: 2, label: '公共空间' },
  { type: 1, label: '团队空间' },
  { type: 0, label: '个人空间' },
]

export function toSpaceOptions(rows: VisibleSpace[]): SpaceOption[] {
  return rows.flatMap((row) => {
    if (row.id == null || !GROUPS.some((group) => group.type === row.type)) return []
    if (typeof row.id === 'number' && !Number.isSafeInteger(row.id)) {
      throw new Error('空间 ID 精度丢失，请检查接口的 Long 序列化配置')
    }
    return [{ label: row.name || '未命名空间', value: String(row.id), type: row.type! }]
  })
}

export function groupSpaces(spaces: SpaceOption[]): SpaceGroup[] {
  return GROUPS.map((group) => ({
    ...group,
    spaces: spaces.filter((space) => space.type === group.type),
  }))
}

export function toFolderOptions(rows: VisibleFolder[]): FolderOption[] {
  return rows.map((row) => {
    if (row.id == null || (typeof row.id === 'number' && !Number.isSafeInteger(row.id))) {
      throw new Error('文件夹 ID 缺失或精度丢失')
    }
    return { id: String(row.id), label: row.name || '未命名文件夹', children: toFolderOptions(row.children ?? []) }
  })
}

export function flattenFolders(rows: FolderOption[], depth = 0, ancestorIds: string[] = []): FlatFolder[] {
  return rows.flatMap((folder) => [
    { ...folder, depth, descendantIds: collectFolderIds(folder), ancestorIds },
    ...flattenFolders(folder.children, depth + 1, [...ancestorIds, folder.id]),
  ])
}

function collectFolderIds(folder: FolderOption): string[] {
  return [folder.id, ...folder.children.flatMap(collectFolderIds)]
}

export function selectAllSpaces(current: SpaceSelection, checked: boolean): SpaceSelection {
  return { allSpaces: checked, selectedIds: [], ...(current.folderIds === undefined ? {} : { folderIds: [] }) }
}

export function selectSpace(current: SpaceSelection, id: string, checked: boolean, containedFolderIds: string[] = []): SpaceSelection {
  const selected = current.allSpaces ? [] : current.selectedIds
  return {
    allSpaces: false,
    selectedIds: checked ? [...new Set([...selected, id])] : selected.filter((item) => item !== id),
    ...(current.folderIds === undefined ? {} : { folderIds: (current.allSpaces ? [] : current.folderIds).filter((folderId) => !checked || !containedFolderIds.includes(folderId)) }),
  }
}

export function selectGroup(current: SpaceSelection, ids: string[], checked: boolean, containedFolderIds: string[] = []): SpaceSelection {
  const selected = current.allSpaces ? [] : current.selectedIds
  return {
    allSpaces: false,
    selectedIds: checked
      ? [...new Set([...selected, ...ids])]
      : selected.filter((id) => !ids.includes(id)),
    ...(current.folderIds === undefined ? {} : { folderIds: (current.allSpaces ? [] : current.folderIds).filter((folderId) => !checked || !containedFolderIds.includes(folderId)) }),
  }
}

export function selectFolder(current: SpaceSelection, id: string, checked: boolean, descendantIds: string[] = []): SpaceSelection {
  const selected = current.allSpaces ? [] : (current.folderIds ?? [])
  return {
    allSpaces: false,
    selectedIds: current.allSpaces ? [] : current.selectedIds,
    folderIds: checked
      ? [...new Set([...selected.filter((item) => !descendantIds.includes(item)), id])]
      : selected.filter((item) => item !== id),
  }
}

export function groupSelectionState(ids: string[], current: SpaceSelection, folderIds: string[] = []) {
  const count = current.allSpaces ? 0 : ids.filter((id) => current.selectedIds.includes(id)).length
  return {
    checked: ids.length > 0 && count === ids.length,
    indeterminate: (count > 0 && count < ids.length) || (count < ids.length && folderIds.some((id) => current.folderIds?.includes(id))),
  }
}

export function isScopeReady(current: SpaceSelection): boolean {
  return current.allSpaces || current.selectedIds.length > 0 || (current.folderIds?.length ?? 0) > 0
}
