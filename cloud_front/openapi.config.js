import { generateService } from '@umijs/openapi'
import { fileURLToPath } from 'node:url'

export const buildOpenApiAuthorization = (env = process.env) => {
  if (env.OPENAPI_AUTHORIZATION) {
    return env.OPENAPI_AUTHORIZATION
  }
  if (!env.DOC_PASSWORD) {
    return undefined
  }
  const username = env.DOC_USERNAME || 'admin'
  const credential = Buffer.from(`${username}:${env.DOC_PASSWORD}`).toString('base64')
  return `Basic ${credential}`
}

export const openApiConfig = {
  requestLibPath: "import request from '@/request'",
  // 生成端口位置
  schemaPath: 'http://localhost:8123/api/v2/api-docs',
  serversPath: './src',
  authorization: buildOpenApiAuthorization(),
  hook: {
    customType(schemaObject, namespace, originGetType) {
      if (schemaObject?.format === 'int64') {
        return 'string | number'
      }
      return originGetType(schemaObject, namespace)
    },
  },
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  generateService(openApiConfig)
}
