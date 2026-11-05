const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const Module = require('node:module')
const { Readable } = require('node:stream')
const vm = require('node:vm')
const esbuild = require('esbuild')
const ts = require('typescript')

const root = path.resolve(__dirname, '..')
const fixture = path.resolve('C:/isolation-fixture')
const envFile = path.join(fixture, '.env.local')
const example = path.join(fixture, 'deploy', '.env.example')
const files = new Map([[example, 'RAG_LLM_API_KEY=\n']])
const operations = []
const fakeFs = {
  existsSync: p => files.has(p),
  readFileSync: p => { assert(files.has(p)); return files.get(p) },
  writeFileSync: (p, v) => { operations.push(['write', p]); files.set(p, v) },
  renameSync: (a, b) => { operations.push(['rename', a, b]); assert(files.has(a)); files.set(b, files.get(a)); files.delete(a) },
  unlinkSync: p => files.delete(p),
}
const built = esbuild.buildSync({
  entryPoints: [path.join(root, 'vite/localEnvPlugin.ts')], bundle: true, write: false,
  platform: 'node', format: 'cjs',
  define: { 'import.meta.url': JSON.stringify(require('node:url').pathToFileURL(path.join(fixture, 'cloud_front/vite/localEnvPlugin.js')).href) },
})
const loaded = new Module('isolated-plugin', module)
loaded.require = id => id === 'node:fs' ? fakeFs : require(id)
loaded._compile(built.outputFiles[0].text, 'isolated-plugin.cjs')
const plugin = loaded.exports
function handlerFor(flag) {
  if (flag === undefined) delete process.env.CLOUDWIKI_DISABLE_LOCAL_ENV
  else process.env.CLOUDWIKI_DISABLE_LOCAL_ENV = flag
  let handler
  plugin.localEnvPlugin().configureServer({ middlewares: { use(_path, fn) { handler = fn } } })
  return handler
}
async function request(handler, payload) {
  const req = Readable.from([JSON.stringify(payload)])
  req.method = 'POST'; req.headers = {}
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('request did not receive a response')), 1000)
    handler(req, {
      setHeader() {}, end(body) { clearTimeout(timeout); resolve({ status: this.statusCode, body: JSON.parse(body) }) },
    })
  })
}
async function main() {
  assert.equal(handlerFor('1'), undefined)
  const handler = handlerFor(undefined)
  assert.equal(typeof handler, 'function')
  for (const [before, after] of [
    ['', 'RAG_LLM_API_KEY=new\n'],
    ['RAG_LLM_API_KEY=old\n', 'RAG_LLM_API_KEY=new\n'],
    ['# comment\n RAG_LLM_API_KEY = old\nRAG_LLM_API_KEY_EXTRA=keep\n# middle\nRAG_LLM_API_KEY=old2\nOTHER=keep\n', '# comment\nRAG_LLM_API_KEY=new\nRAG_LLM_API_KEY_EXTRA=keep\n# middle\nOTHER=keep\n'],
  ]) {
    files.set(envFile, before); operations.length = 0
    const r = await request(handler, { key: 'RAG_LLM_API_KEY', value: 'new' })
    assert.equal(r.status, 200); assert(!JSON.stringify(r.body).includes('new'))
    assert.equal(files.get(envFile), after)
    assert.deepEqual(operations.map(x => x[0]), ['write', 'rename'])
    assert.notEqual(operations[0][1], envFile); assert.equal(operations[1][1], operations[0][1]); assert.equal(operations[1][2], envFile)
  }
  for (const payload of [null, false, 42, 'text', [], {key:'BAD',value:'x'}, {key:'RAG_LLM_API_KEY',value:' '}, {key:'RAG_LLM_API_KEY',value:'\nx'}]) {
    const before = files.get(envFile); operations.length = 0
    assert.equal((await request(handler, payload)).status, 400)
    assert.equal(files.get(envFile), before); assert.equal(operations.length, 0)
  }
  const vue = fs.readFileSync(path.join(root, 'src/components/ModelSelector.vue'), 'utf8')
  const script = vue.match(/<script\b(?=[^>]*\bsetup\b)(?=[^>]*\blang="ts")[^>]*>([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile('component.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const functions = ast.statements.filter(n => ts.isFunctionDeclaration(n) && ['callLocalEnv', 'refreshEnvStatus'].includes(n.name?.text))
  assert.equal(functions.length, 2)
  const code = ts.transpile(functions.map(n => n.getFullText(ast)).join('\n'))
  const context = { LOCAL_ENV_ENDPOINT: '/fixture', isLocalEnvWritable: true, envWritable: {value:true}, envStatus: {value:{}}, fetch: async () => ({ok:false,status:404,json:async()=>{throw Error('not JSON')}}) }
  vm.createContext(context); vm.runInContext(code, context)
  await context.refreshEnvStatus(); assert.equal(context.envWritable.value, false)
  assert.match(vue, /v-if="!envWritable"/); assert.match(vue, /:disabled="!envWritable"/)
  console.log('local env isolation: writes, validation, registration and UI unavailability passed')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
