import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [loginUserStoreSource, mainSource] = await Promise.all([
  readSource('./src/stores/useLoginUserStore.ts'),
  readSource('./src/main.ts'),
])

test('login user fetch keeps route startup alive when the backend is unavailable', () => {
  const fetchLoginUserBlock = loginUserStoreSource.match(
    /async function fetchLoginUser\(\) \{[\s\S]*?\n  \}/,
  )?.[0]

  assert.ok(fetchLoginUserBlock, 'fetchLoginUser function should exist')
  assert.match(fetchLoginUserBlock, /try\s*\{[\s\S]*getUserLoginUsingGet\(\)/)
  assert.match(fetchLoginUserBlock, /catch\b/)
  assert.match(fetchLoginUserBlock, /loginUser\.value\s*=\s*\{\s*userName:\s*'未登录'/)
})

test('vue plugins are installed before the app is mounted', () => {
  const cropperIndex = mainSource.indexOf('app.use(VueCropper)')
  const mountIndex = mainSource.indexOf("app.mount('#app')")

  assert.notEqual(cropperIndex, -1, 'VueCropper plugin should be registered')
  assert.notEqual(mountIndex, -1, 'Vue app should be mounted')
  assert.ok(cropperIndex < mountIndex, 'VueCropper should be registered before mount')
})
