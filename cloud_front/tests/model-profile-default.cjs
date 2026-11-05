const assert = require('node:assert/strict')
const path = require('node:path')
const Module = require('node:module')
const esbuild = require('esbuild')
const { createPinia, setActivePinia } = require('pinia')

const frontend = path.resolve(__dirname, '..')
function load(entry) {
  const result = esbuild.buildSync({
    entryPoints: [path.join(frontend, entry)],
    bundle: true,
    write: false,
    platform: 'node',
    format: 'cjs',
    external: ['vue', 'pinia'],
    alias: { '@': path.join(frontend, 'src') },
    define: {
      'import.meta.env.DEV': 'true',
      'import.meta.env.VITE_ENABLE_MODEL_SELECTOR': 'undefined',
    },
  })
  const loaded = new Module(path.join(__dirname, 'in-memory.cjs'), module)
  loaded.filename = path.join(__dirname, 'in-memory.cjs')
  loaded.paths = module.paths
  loaded._compile(result.outputFiles[0].text, loaded.filename)
  return loaded.exports
}

const profiles = load('src/constants/modelProfiles.ts')
const { useModelProfileStore } = load('src/stores/useModelProfileStore.ts')
const expected = 'cloud-deepseek-flash'
const local = 'local-qwen3-1.7b'

assert.equal(profiles.DEFAULT_MODEL_PROFILE_ID, expected, 'default ID')
assert.equal(profiles.getModelProfile('unknown-id').id, expected, 'unknown profile')
assert.equal(profiles.getModelProfile(local).id, local, 'known local profile')

function makeStore(selected, unavailable = false) {
  const values = new Map()
  if (selected !== null) values.set(profiles.MODEL_PROFILE_STORAGE_KEY, selected)
  global.window = {
    localStorage: {
      getItem(key) {
        if (unavailable) throw new Error('storage unavailable')
        return values.get(key) ?? null
      },
      setItem(key, value) {
        if (unavailable) throw new Error('storage unavailable')
        values.set(key, value)
      },
    },
  }
  setActivePinia(createPinia())
  return useModelProfileStore()
}

assert.equal(makeStore(null).selected.id, expected, 'no stored selection')
assert.equal(makeStore('unknown-id').selected.id, expected, 'invalid stored ID')
assert.equal(makeStore(null, true).selected.id, expected, 'storage unavailable')
const saved = makeStore(local)
assert.equal(saved.selected.id, local, 'valid stored selection')
saved.selectedId = 'no-longer-present'
assert.equal(saved.selected.id, expected, 'selection no longer exists')

const store = makeStore(null)
const custom = store.addCustomProfile({
  label: 'Test', model: 'test', baseUrl: 'http://localhost',
  provider: 'other', location: 'local', note: '',
})
store.setSelectedId(custom.id)
assert.equal(store.selected.id, custom.id, 'custom profile selected before deletion')
store.removeCustomProfile(custom.id)
assert.equal(store.selected.id, expected, 'selected custom profile deleted')
assert.equal(store.selectedId, expected, 'default ID selected after deletion')

console.log('Model profile defaults: A1 and A2 passed')
