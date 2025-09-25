import { generateService } from '@umijs/openapi'

generateService({
  requestLibPath: "import request from '@/request'",
  // 生成端口位置
  schemaPath: 'http://localhost:8123/api/v2/api-docs',
  serversPath: './src',
})
