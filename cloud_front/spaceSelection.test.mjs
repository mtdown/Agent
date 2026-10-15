import test from 'node:test'
import assert from 'node:assert/strict'
import {
  groupSpaces,
  groupSelectionState,
  isScopeReady,
  selectAllSpaces,
  selectGroup,
  selectSpace,
  selectFolder,
  toFolderOptions,
  flattenFolders,
  toSpaceOptions,
} from './src/pages/aiAssistant/spaceSelection.ts'

const publicId = '2095512793587744770'
const teamId = '2095512793587744771'
const personalId = '2095512793587744772'

test('keeps large space ids as exact strings and groups by visible type', () => {
  const spaces = toSpaceOptions([
    { id: teamId, name: '政策文档', type: 1 },
    { id: publicId, name: '公开文档', type: 2 },
    { id: personalId, name: '个人区', type: 0 },
  ])
  assert.deepEqual(groupSpaces(spaces).map(group => [group.label, group.spaces.map(space => space.value)]), [
    ['公共空间', [publicId]],
    ['团队空间', [teamId]],
    ['个人空间', [personalId]],
  ])
  assert.throws(() => toSpaceOptions([{ id: Number(publicId), name: '坏 ID', type: 2 }]), /精度/)
})

test('selecting one child from all scopes sends only that child', () => {
  assert.deepEqual(selectSpace({ allSpaces: true, selectedIds: [] }, teamId, true), {
    allSpaces: false,
    selectedIds: [teamId],
  })
})

test('group check selects only its children and supports partial selection', () => {
  const fromAll = selectGroup({ allSpaces: true, selectedIds: [] }, [teamId, personalId], true)
  assert.deepEqual(fromAll, { allSpaces: false, selectedIds: [teamId, personalId] })
  const partial = selectSpace(fromAll, personalId, false)
  assert.deepEqual(groupSelectionState([teamId, personalId], partial), {
    checked: false,
    indeterminate: true,
  })
  assert.deepEqual(selectGroup(partial, [teamId, personalId], false).selectedIds, [])
})

test('switching out of all scopes starts empty and switching back clears manual selection', () => {
  assert.deepEqual(selectAllSpaces({ allSpaces: true, selectedIds: [] }, false), {
    allSpaces: false,
    selectedIds: [],
  })
  assert.deepEqual(selectAllSpaces({ allSpaces: false, selectedIds: [publicId] }, true), {
    allSpaces: true,
    selectedIds: [],
  })
})

test('manual scope cannot submit an empty selection to classic RAG or Agent', () => {
  assert.equal(isScopeReady({ allSpaces: false, selectedIds: [] }), false)
  assert.equal(isScopeReady({ allSpaces: false, selectedIds: [publicId] }), true)
  assert.equal(isScopeReady({ allSpaces: true, selectedIds: [] }), true)
})

test('folder tree keeps hierarchy and exact ids', () => {
  const tree = toFolderOptions([{ id: personalId, name: '合同', children: [
    { id: teamId, name: '模板', children: [] },
  ] }])
  assert.deepEqual(flattenFolders(tree).map(folder => [folder.id, folder.label, folder.depth]), [
    [personalId, '合同', 0], [teamId, '模板', 1],
  ])
})

test('selecting two folders keeps both without selecting their entire space', () => {
  const first = selectFolder({ allSpaces: true, selectedIds: [], folderIds: [] }, personalId, true)
  const second = selectFolder(first, teamId, true)
  assert.deepEqual(second, { allSpaces: false, selectedIds: [], folderIds: [personalId, teamId] })
  assert.equal(isScopeReady(second), true)
  assert.deepEqual(selectAllSpaces(second, true), { allSpaces: true, selectedIds: [], folderIds: [] })
})

test('selecting a whole space replaces its narrower folder selection', () => {
  const folderOnly = selectFolder({ allSpaces: true, selectedIds: [], folderIds: [] }, personalId, true)
  const wholeSpace = selectSpace(folderOnly, publicId, true, [personalId])
  assert.deepEqual(wholeSpace, { allSpaces: false, selectedIds: [publicId], folderIds: [] })
})
