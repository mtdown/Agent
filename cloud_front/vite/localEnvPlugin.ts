import { fileURLToPath, URL } from 'node:url'
import * as fs from 'node:fs'
import * as path from 'node:path'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Plugin } from 'vite'

/** 本文件位于 cloud_front/vite/，上两级即项目根（与 start-dev.ps1 的 $root 一致）。 */
const ROOT_DIR = fileURLToPath(new URL('../../', import.meta.url))
const LOCAL_ENV_FILE = path.join(ROOT_DIR, '.env.local')
const ENV_EXAMPLE_FILE = path.join(ROOT_DIR, 'deploy', '.env.example')

export const LOCAL_ENV_ENDPOINT = '/__cw/local-env'

/** 可写入的键名来自云端同一份登记表，避免前端写入任意环境变量或 VITE_ 前缀。 */
function readAllowedKeys(): Set<string> {
  if (!fs.existsSync(ENV_EXAMPLE_FILE)) return new Set()
  return new Set(
    fs.readFileSync(ENV_EXAMPLE_FILE, 'utf8')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#') && line.includes('='))
      .map((line) => line.split('=', 1)[0].trim())
      .filter((key) => key.endsWith('_API_KEY') && !key.startsWith('VITE_'))
  )
}

function readLocalEnv(): Map<string, string> {
  const result = new Map<string, string>()
  if (!fs.existsSync(LOCAL_ENV_FILE)) return result
  for (const line of fs.readFileSync(LOCAL_ENV_FILE, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) continue
    const [key, ...rest] = trimmed.split('=')
    result.set(key.trim(), rest.join('=').trim())
  }
  return result
}

/** 只替换第一个同名键并移除其余重复项，保留其他行的内容和顺序。 */
export function upsertEnvContent(content: string, key: string, value: string): string {
  const newline = content.includes('\r\n') ? '\r\n' : '\n'
  const lines = content ? content.split(/\r?\n/) : []
  if (lines.length && lines[lines.length - 1] === '') lines.pop()
  let hit = false
  const next: string[] = []
  for (const line of lines) {
    const equals = line.indexOf('=')
    if (equals >= 0 && line.slice(0, equals).trim() === key) {
      if (!hit) next.push(`${key}=${value}`)
      hit = true
    } else {
      next.push(line)
    }
  }
  if (!hit) next.push(`${key}=${value}`)
  return next.join(newline) + newline
}

/** upsert 单个键；先写临时文件再 rename。 */
function upsertLocalEnv(key: string, value: string): void {
  const current = fs.existsSync(LOCAL_ENV_FILE) ? fs.readFileSync(LOCAL_ENV_FILE, 'utf8') : ''
  const content = upsertEnvContent(current, key, value)
  const tmp = `${LOCAL_ENV_FILE}.${process.pid}.tmp`
  try {
    fs.writeFileSync(tmp, content, 'utf8')
    fs.renameSync(tmp, LOCAL_ENV_FILE)
  } finally {
    if (fs.existsSync(tmp)) fs.unlinkSync(tmp)
  }
}

function sendJson(res: ServerResponse, status: number, body: unknown): void {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.setHeader('Cache-Control', 'no-store')
  res.end(JSON.stringify(body))
}

async function readBody(req: IncomingMessage): Promise<string> {
  let data = ''
  for await (const chunk of req) {
    data += chunk
    if (data.length > 4096) throw new Error('body too large')
  }
  return data
}

/**
 * 浏览器无法写磁盘，但 dev server 是 Node 进程。前端「模型管理」把明文 POST 到这里，
 * 由它 upsert 到项目根 .env.local（已被 gitignore）；响应只回「是否配置」布尔，不回传值。
 * apply:'serve' + configureServer 保证 build / preview 不注册该端点，云端产物零端点。
 */
export function localEnvPlugin(): Plugin {
  return {
    name: 'cloudwiki-local-env',
    apply: 'serve',
    configureServer(server) {
      if (process.env.CLOUDWIKI_DISABLE_LOCAL_ENV === '1') return
      server.middlewares.use(LOCAL_ENV_ENDPOINT, (req, res) => {
        if (req.method === 'GET') {
          const current = readLocalEnv()
          const keys = [...readAllowedKeys()].map((key) => ({
            key,
            configured: (current.get(key) ?? '').length > 0,
          }))
          sendJson(res, 200, { ok: true, keys })
          return
        }
        if (req.method !== 'POST') {
          sendJson(res, 405, { ok: false, error: 'method not allowed' })
          return
        }
        void (async () => {
          let raw: string
          try {
            raw = await readBody(req)
          } catch {
            sendJson(res, 400, { ok: false, error: '请求体过大' })
            return
          }
          let parsed: unknown
          try {
            parsed = JSON.parse(raw || '{}') as unknown
          } catch {
            sendJson(res, 400, { ok: false, error: '请求体解析失败' })
            return
          }
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            sendJson(res, 400, { ok: false, error: '请求体格式不合法' })
            return
          }
          const payload = parsed as { key?: unknown; value?: unknown }
          const key = typeof payload.key === 'string' ? payload.key.trim() : ''
          const rawValue = typeof payload.value === 'string' ? payload.value : ''
          const value = rawValue.trim()
          if (!readAllowedKeys().has(key)) {
            sendJson(res, 400, { ok: false, error: '键名不在允许清单内' })
            return
          }
          if (!value || value.length > 512 || /[\r\n]/.test(rawValue)) {
            sendJson(res, 400, { ok: false, error: '密钥值不合法' })
            return
          }
          try {
            upsertLocalEnv(key, value)
          } catch {
            sendJson(res, 500, { ok: false, error: '写入本地密钥文件失败' })
            return
          }
          sendJson(res, 200, { ok: true, key, configured: true })
        })()
      })
    },
  }
}
