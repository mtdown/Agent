# Design: add-open-rag-api

## D1. 认证模型

```
本地 Agent                     云策库后端
   |  X-API-Key: cpk_xxxxxxxx      |
   |------ POST /api/open/rag/search ------>|
   |                                 | SHA-256(key) 查 rag_api_key (key_hash 索引)
   |                                 | 命中 + 未删除 → 取 userId → User
   |                                 | 失败 → 40101 无权限（统一文案，不区分 key 无效/已删）
   |<---- BaseResponse<RagSearchResult> ----|
```

- **不做** sa-token 融合：开放接口独立 Controller，不依赖 cookie/session，认证在 Controller 入口显式解析一次
- key 属主的 User 直接传入既有 Service 层（`RagSearchService.search(loginUser, req)` / `RagAskService.ask(loginUser, req)`），**权限过滤零新增代码**——这是阶段 1 把过滤做在 Service 层的回报

## D2. 数据模型 `rag_api_key`

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | ASSIGN_ID |
| userId | bigint NOT NULL | 属主（idx） |
| keyName | varchar(64) | 用途名，如"本地 Agent" |
| keyHash | char(64) | SHA-256 hex（idx，认证唯一入口） |
| keyPrefix | varchar(16) | 明文前 8 位，列表展示用 |
| createTime / updateTime / isDelete | 常规 | 软删 |

- 明文格式：`cpk_` + 32 字节 SecureRandom hex（共 68 字符）；**不存明文**，创建响应一次性返回
- 删除 = 软删；认证查询条件 `keyHash = ? AND isDelete = 0`，删除即吊销

## D3. 接口

### Key 管理（登录态，挂在 `/rag/key/*`）

| 接口 | 入参 | 出参 |
|---|---|---|
| POST `/rag/key/create` | `{keyName}` | `{id, keyName, keyPrefix, apiKey(明文,仅此一次), createTime}` |
| GET `/rag/key/list` | - | `[{id, keyName, keyPrefix, createTime}]` |
| POST `/rag/key/delete` | `{id}` | `Boolean`（仅属主可删） |

### 开放接口（X-API-Key 认证，挂在 `/open/rag/*`）

| 接口 | 说明 |
|---|---|
| POST `/open/rag/search` | body 同 RagSearchRequest（query/spaceIds/topK），返回 RagSearchResult |
| POST `/open/rag/ask` | body 同 RagAskRequest（deepThinking 可用），SSE 流（meta/reason/delta/done/error）与 /rag/ask 完全一致 |

## D4. 前端「开放 API」抽屉（AI 助手页）

- scope-bar 右侧「开放 API」按钮 → a-drawer
- 列表：名称 / 前缀 `cpk_xxxx…` / 创建时间 / 删除（二次确认）
- 创建：输入名称 → 弹明文 key 一次性展示（复制按钮 + "关闭后无法再次查看"警示）
- 底部折叠的 curl 调用示例（search + ask 各一段，可复制）

## D5. 安全考量

- 哈希存储：库泄露不暴露可用 key
- 统一 40101 错误文案，不区分 key 不存在/已删除/格式错误（防枚举）
- 只读面：开放 Controller 只有 search/ask 两个读接口，无任何写操作
- 限流：**非目标**（演示规模）；扩展点——未来加 Bucket4L 或网关层即可，接口签名不变

## D6. 替代方案

- **sa-token 二级账号**：为 Agent 发系统账号走密码登录拿 token——引入 token 刷新/过期问题，本地脚本复杂化，放弃
- **JWT 静态签名**：要引入 JWT 依赖与密钥管理，对单机演示是过度设计，放弃
- **key 明文存储**：省一次 SHA-256，但库泄露即全部 key 作废，放弃
