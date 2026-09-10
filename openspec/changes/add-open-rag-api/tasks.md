# Tasks: add-open-rag-api

## 1. 数据模型与 Key 服务

- [x] 1.1 建表 SQL `cloud/sql/create_table_rag_api_key.sql`（userId/keyName/keyHash char(64)/keyPrefix/常规三列 + key_hash 索引）+ 实体 `RagApiKey` + Mapper
- [x] 1.2 `RagApiKeyService`：create（SecureRandom 生成 `cpk_`+32B hex，SHA-256 存哈希，明文只随创建响应出一次）/ list（不含明文）/ delete（仅属主，软删）/ resolveUser（哈希查表，未命中统一抛 40101）
- [x] 1.3 RagApiKeyService 单测：创建返回明文且库里只有哈希、resolveUser 正确命中、错误/已删 key 统一 40101、非属主删除失败

## 2. 接口层

- [x] 2.1 Key 管理 Controller `/rag/key/*`（登录态）：create/list/delete，BaseResponse 风格与现有一致
- [x] 2.2 开放 Controller `/open/rag/*`：`X-API-Key` 头认证 → resolveUser → 复用 RagSearchService / RagAskService；search 返回 BaseResponse<RagSearchResult>，ask 返回同协议 SSE
- [x] 2.3 Controller 层冒烟单测（MockMvc 或 Service mock）：无 key 40101、有效 key 检索透传、ask 返回 SseEmitter

## 3. 前端「开放 API」抽屉

- [x] 3.1 AI 助手页 scope-bar 加「开放 API」按钮 + a-drawer：key 列表（名称/前缀/创建时间/删除带确认）
- [x] 3.2 创建流程：名称输入 → 明文 key 一次性弹层（复制按钮 + 无法再次查看警示）
- [x] 3.3 curl 调用示例折叠块（search + ask 两段，含 X-API-Key 头示例），复制按钮
- [x] 3.4 前端 vue-tsc 通过

## 4. 验证与收尾

- [x] 4.1 后端全量 `mvn test` **180/180**（新增 9：Key 服务 5 + Controller 4）；前端 vue-tsc 通过；建表已落 3307/Cloud（JDBC 直连执行）；`openspec validate --strict` 通过 — 2026-09-10
  - 测试坑：Mockito 2+ 的 any(HttpServletRequest.class) **不匹配 null** 实参——测试传 null 会静默走 unstubbed 路径返回 null，断言失败点错位；mock request 对象传入即可。 `mvn test` 通过；`openspec validate add-open-rag-api --strict` 通过
- [x] 4.2 真实冒烟：应用内建 key → curl 带钥匙调 search（有效/无效/删除后）与 ask SSE；非授权 key 属主检索范围过滤验证
  - 2026-09-10 重启后冒烟全过（7 项）：①创建 key 明文 `cpk_` 一次性返回 ②key 列表仅显前缀无明文 ③open/rag/search 带 key 命中 24 号文档 ④open/rag/ask SSE 流式（meta→delta→done，快速模型，usage=2343/30 tokens）⑤无效 key 40101 ⑥缺 key 40101（统一文案防枚举）⑦删除后 key 再调 40101（软删即吊销）。
- [x] 4.3 汇报手测清单，负责人页面手测（抽屉创建/复制/删除、curl 示例）
  - 2026-09-10 负责人手测反馈 3 项，修复如下（Round 2）：
    1. **curl 示例地址误导**（示例指向前端 3000，Vite 无 /api 代理，纯 HTTP 客户端 404）：`apiBase` 改从 axios 实例 `baseURL`（=8123）取值，ragController 导出 `apiBaseUrl`，换环境自动跟随。
    2. **LLM 间歇性 Connection reset**（实测 DeepSeek 3/3 亚秒全通，日志仅 1 次，属瞬时网络抖动）：RagLlmClient 重构为 `doStream` 返回结果状态——未输出任何内容即失败自动重试 1 次；已输出后失败直接报错不重试（防重复输出）；用户主动中断不重试。顺手修**流截断静默成功**缺陷：流结束时既无 finish_reason 也无 [DONE] 判为失败（此前默认当 stop 成功，中途断流会被当成完整回答）。
    3. **Windows Git Bash 内联中文 JSON 编码错误**：示例 Content-Type 加 `charset=utf-8`、改 `--data-binary @body.json` 形式，抽屉加 Windows 终端提示行。
  - 验证：后端全量 **182/182**（新增：连接失败重试 1、部分输出不重试 1），前端 vue-tsc 通过。
- [x] 4.4 负责人确认后上传任务分支 — 2026-09-10 提交 e90c901 推送成功（本地/远程 HEAD 一致）
