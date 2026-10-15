import test from 'node:test'
import assert from 'node:assert/strict'
import { parseCitationSnapshot } from './src/pages/aiAssistant/citationIds.ts'

const exactId = '2095512793587744770'

test('restores exact document ids from older numeric citation snapshots', () => {
  const json = `[{"index":1,"docId":${exactId},"docTitle":"政策文件"}]`
  assert.equal(parseCitationSnapshot(json)[0].docId, exactId)
})

test('keeps new string ids and citation text intact', () => {
  const json = JSON.stringify([{ index: 1, docId: exactId, docTitle: '政策文件', chunkText: '含文号及引用' }])
  assert.deepEqual(parseCitationSnapshot(json), JSON.parse(json))
})
