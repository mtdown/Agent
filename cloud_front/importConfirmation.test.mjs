import test from 'node:test'
import assert from 'node:assert/strict'
import { importConfirmationAction, importConfirmationStatus, confirmationLockKey, persistConfirmationLock } from './src/pages/aiAssistant/importConfirmation.ts'

const card = { kind: 'import_confirmation', turnId: '123', spaceLabel: '团队', folderName: 'A/B',
  folderSegments: ['A/B', 'C'], urls: ['https://example.org/a','https://example.org/b'],
  expiresAt: 2000, consumed: false, status: 'pending', confirmToken: 'confirm', cancelToken: 'cancel' }

test('only pending fresh confirmations expose their matching server token', () => {
  assert.deepEqual(importConfirmationAction(card,'confirm',1000,false), { turnId:'123', token:'confirm' })
  assert.deepEqual(importConfirmationAction(card,'cancel',1000,false), { turnId:'123', token:'cancel' })
  assert.equal(importConfirmationAction(card,'confirm',2000,false), null)
  assert.equal(importConfirmationAction(card,'confirm',1000,true), null)
  assert.equal(importConfirmationAction({...card,consumed:true},'confirm',1000,false), null)
  assert.equal(importConfirmationAction({...card,status:'unknown'},'confirm',1000,false), null)
  assert.equal(importConfirmationAction({...card,confirmToken:undefined},'confirm',1000,false), null)
})
test('status distinguishes pending, cancelled, expired, unknown and uncertain request', () => {
  assert.equal(importConfirmationStatus(card,1000,false),'pending')
  assert.equal(importConfirmationStatus(card,2000,false),'expired')
  assert.equal(importConfirmationStatus({...card,status:'cancelled',consumed:true},1000,false),'cancelled')
  assert.equal(importConfirmationStatus({...card,status:'unknown',consumed:true},1000,false),'unknown')
  assert.equal(importConfirmationStatus(card,1000,true),'unknown')
  assert.notEqual(confirmationLockKey('1','23'),confirmationLockKey('12','3'))
})
test('uncertain request lock survives a reload and fails closed when storage is unavailable', () => {
  const items = new Map()
  const storage = { setItem:(key,value)=>items.set(key,value), getItem:key=>items.get(key) ?? null }
  const key = confirmationLockKey('8',card.turnId)
  assert.equal(persistConfirmationLock(storage,key),true)
  assert.equal(importConfirmationAction(card,'confirm',1000,storage.getItem(key) === '1'),null)
  assert.equal(importConfirmationAction(card,'cancel',1000,storage.getItem(key) === '1'),null)
  assert.equal(persistConfirmationLock({setItem:()=>{throw new Error('blocked')},getItem:()=>null},key),false)
})


import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import ts from 'typescript'
import { compileTemplate } from '@vue/compiler-sfc'
import { createSSRApp, createRenderer, h } from 'vue'
import { renderToString } from '@vue/server-renderer'

test('card renders every URL and separately escaped directory levels', async () => {
  const source = await readFile(new URL('./src/pages/aiAssistant/AiAssistantPage.vue', import.meta.url), 'utf8')
  const start = source.indexOf('<div v-if="msg.interaction?.kind === \'import_confirmation\'"')
  const end = source.indexOf('<div v-if="msg.interaction?.kind === \'wiki_write_result\'', start)
  assert.ok(start >= 0 && end > start)
  const compiled = compileTemplate({source: source.slice(start,end), filename:'card.vue', id:'card'})
  assert.deepEqual(compiled.errors,[])
  const js = ts.transpileModule(compiled.code,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText
  const exports = {}
  new Function('exports','require',js)(exports,createRequire(import.meta.url))
  const app = createSSRApp({
    data: () => ({ msg: {interaction:card}, streaming:false }),
    methods: {confirmationStatus:()=> 'pending', submitImportConfirmation:()=>{}},
    render: exports.render,
  })
  app.component('a-button', { render() { return h('button', this.$attrs, this.$slots.default?.()) } })
  const html = await renderToString(app)
  assert.match(html,/确认前不会写入 Wiki/)
  assert.match(html,/确认导入/)
  assert.match(html,/取消/)
  for (const url of card.urls) assert.ok(html.includes(url))
  assert.ok(html.includes('>A/B</span>') && html.includes('>C</span>'))
  for (const [status, text] of [['cancelled','已取消，未导入。'],['expired','确认已过期'],['unknown','结果尚未确认']]) {
    const terminal = createSSRApp({
      data: () => ({ msg: {interaction:card}, streaming:false }),
      methods: {confirmationStatus:()=> status, submitImportConfirmation:()=>{ throw new Error('must not submit') }},
      render: exports.render,
    })
    terminal.component('a-button', { render() { return h('button', this.$attrs, this.$slots.default?.()) } })
    const output = await renderToString(terminal)
    assert.ok(output.includes(text))
    assert.ok(!output.includes('<button'))
  }
  const nodes = []
  const renderer = createRenderer({
    createElement: tag => ({ tag, children: [], props: {} }),
    createText: text => ({ text }), createComment: text => ({ text }),
    setText: (node, text) => { node.text = text },
    setElementText: (node, text) => { node.text = text },
    patchProp: (node, key, _old, value) => { node.props[key] = value },
    insert: (child, parent) => { parent.children.push(child); child.parent = parent },
    remove: () => {}, parentNode: node => node.parent, nextSibling: () => null,
  })
  const actions = []
  const interactive = renderer.createApp({
    data: () => ({ msg:{interaction:card}, streaming:false }),
    methods: {confirmationStatus:()=> 'pending', submitImportConfirmation:(_msg, action)=>actions.push(action)},
    render: exports.render,
  })
  interactive.component('a-button', { render() { return h('button', this.$attrs, this.$slots.default?.()) } })
  const root = {children:nodes}
  interactive.mount(root)
  const collect = node => [node, ...(node.children ?? []).flatMap(collect)]
  const buttons = root.children.flatMap(collect).filter(node => node.tag === 'button')
  assert.equal(buttons.length,2)
  buttons[0].props.onClick()
  buttons[1].props.onClick()
  assert.deepEqual(actions,['confirm','cancel'])
  interactive.unmount()
})

test('SSE choice dispatch preserves old choice and new confirmation and never resends', async () => {
  let source = await readFile(new URL('./src/api/ragController.ts',import.meta.url),'utf8')
  source = source.replace("import request from '@/request'", "const request = { defaults: { baseURL: '' } }")
  const js = ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText
  globalThis.window = {location:{origin:'http://127.0.0.1'}}
  const api = await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
  let calls=0, received, oldChoice, submitted
  globalThis.fetch = async (_url, options) => {
    calls++; submitted=JSON.parse(options.body)
    return new Response('event: wiki_choice\ndata: '+JSON.stringify(card)+'\n\n'
      +'event: wiki_choice\ndata: '+JSON.stringify({kind:'destination_choice',turnId:'9',options:[]})+'\n\n'
      +'event: done\ndata: {}\n\n')
  }
  await api.agentSelectWikiChoiceUsingStream('8','123','confirm', {
    onImportConfirmation: (value)=>{received=value},
    onWikiChoice: (value)=>{oldChoice=value},
  })
  assert.deepEqual(submitted,{turnId:'123',token:'confirm'})
  assert.deepEqual(received,card)
  assert.equal(oldChoice.kind,'destination_choice')
  assert.equal(calls,1)
  globalThis.fetch = async () => { calls++; throw new Error('disconnect') }
  await assert.rejects(api.agentSelectWikiChoiceUsingStream('8','123','confirm',{}),/disconnect/)
  assert.equal(calls,2)
  let error
  globalThis.fetch = async () => { calls++; return new Response('',{status:503}) }
  await api.agentSelectWikiChoiceUsingStream('8','123','cancel',{onError: value=>{error=value}})
  assert.match(error,/503/)
  assert.equal(calls,3)
})


test('real confirmation handler locks before sending, refreshes after disconnect and never resends', async () => {
  const source=await readFile(new URL('./src/pages/aiAssistant/AiAssistantPage.vue',import.meta.url),'utf8')
  const start=source.indexOf('const submitImportConfirmation = async')
  const end=source.indexOf('\nconst selectWikiDestination',start)
  assert.ok(start>=0 && end>start)
  const js=ts.transpileModule(source.slice(start,end)+'\nexports.submit = submitImportConfirmation;',
    {compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
  const output={}, streaming={value:false}, abortController={value:null}, agentMessages={value:[]}
  const agentConversationId={value:'8'}, locks=new Set(), refreshes=[], sent=[]
  let failRefresh = false
  const deps={
    agentConversationId, streaming, abortController, agentMessages,
    reactive:x=>x,
    importConfirmationAction,
    confirmationLocked:turn=>locks.has(turn),
    lockConfirmation:(_conversation,turn)=>{locks.add(turn);return true},
    agentSelectWikiChoiceUsingStream:async (_conversation,turn,token)=>{
      assert.ok(locks.has(turn)); sent.push({turn,token}); throw new Error('connection lost')
    },
    openAgentConversation:async conversation=>{refreshes.push(conversation);if(failRefresh)throw new Error('history unavailable')},
    scrollToBottom:()=>{}, antMessage:{error:()=>{}},
  }
  new Function('exports',...Object.keys(deps),js)(output,...Object.values(deps))
  const msg={interaction:{...card,expiresAt:Date.now()+60000}}
  await output.submit(msg,'confirm')
  assert.deepEqual(sent,[{turn:'123',token:'confirm'}])
  assert.deepEqual(refreshes,['8'])
  assert.equal(streaming.value,false)
  assert.equal(agentMessages.value[0].status,'error')
  await output.submit(msg,'confirm')
  assert.equal(sent.length,1)

  // A failed history refresh leaves the same lock and never initiates another request.
  locks.clear(); failRefresh=true
  await assert.rejects(output.submit(msg,'cancel'),/history unavailable/)
  assert.equal(sent[1].token,'cancel')
  assert.ok(locks.has('123'))
  await output.submit(msg,'cancel')
  assert.equal(sent.length,2)
})

