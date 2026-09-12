# 任务清单：switch-embedding-dashscope

## 1. 配置与密钥通道

- [x] 1.1 `application.yml` 与 `RagProperties.Embedding` 默认值切云端（DashScope + `qwen3.7-text-embedding-flash`），注释同步；`RagEmbeddingClientTest` 相关断言更新后通过
- [x] 1.2 新建根目录 `.env.dev`（写入 `RAG_EMBEDDING_API_KEY`），`.gitignore` 增加该文件；`git status` 确认其未被跟踪
- [x] 1.3 `start-dev.ps1` 在启动后端前解析 `.env.dev` 并注入环境变量（缺失时提示跳过），脚本 ASCII 约束保持
  - 修正：解析改用 `[System.IO.File]::ReadAllLines`（UTF-8）——PowerShell 5.1 `Get-Content` 默认 ANSI 读取含非 ASCII 字节时会破坏行边界，导致 key 整行丢失、注入静默失效；`.env.dev` 同时改为纯 ASCII 注释（双保险）

## 2. 报错可读性

- [x] 2.1 `RagEmbeddingClient` 连接类异常 message 为空时拼异常类名与目标端点；新增单测覆盖"message 为 null"场景并通过

## 3. 强制重嵌入

- [x] 3.1 `WikiRagIndexService.rebuildAll(boolean force)` + Impl 跳过逻辑 + `WikiRagAdminController` `force` 查询参数；`WikiRagIndexServiceImplTest` 覆盖 force=true 不跳过 / 默认仍跳过并通过

## 4. 分步计时

- [x] 4.1 `RagSearchResult` 增 `SearchTimings`（permission/docCount/docNumber/embed/vector/total），`RagSearchServiceImpl` 埋点；`RagSearchServiceImplTest` 断言 timings 存在并通过
- [x] 4.2 `RagAskServiceImpl` 汇总时间线：startedAt/finishedAt/totalMs/retrievalMs/promptMs/firstTokenMs/llmMs/steps 进 `DonePayload`，零命中短路同样携带；`RagAskServiceImplTest` 断言 done 载荷字段并通过
- [x] 4.3 前端 `AiAssistantPage.vue`：消息对象存 trace，统计行加"总计"，新增可展开"调用明细"时间线面板；`npm run build` 通过
- [x] 4.4 后端全量单测（`mvn test` 186 个）通过

## 5. 数据与验证

- [x] 5.1 `python eval/scripts/smoke_test.py` 确认 LLM/Embedding 连通（qwen3.7-text-embedding-flash，维度 1024）
- [x] 5.2 `stop-dev.ps1` + `start-dev.ps1` 起服务（`.env.dev` 生效；修复注入 bug 后重启）
- [x] 5.3 admin 登录后 `POST /api/admin/rag/rebuild?force=true`：total=216 / created=216 / skipped=0 / failed=[]，耗时 76s；库内核对 ACTIVE 2061 chunks、216 docs、维度 1024
- [x] 5.4 `run_eval.py --probe` 通过后执行 http 模式：`results/baseline-20260912-151958-http.json` + `audit/baseline-report.md`（对比结论见汇报：docRecall@6 0.880→0.853，docRecall@1 0.427→0.467，MRR 0.434→0.397，权限零泄漏保持）
- [x] 5.5 SSE 自动化验证：文号题（embed/vector 跳过语义正确）与语义题（embed 270ms / vector 444ms）done 载荷均携带完整时间线；页面视觉展示待负责人手测

## 6. 记录与收尾

- [x] 6.1 `IssueLog.xlsx` 记录"embedding 请求失败: null"问题及解决方案
- [ ] 6.2 汇报测试结果、问题与手测清单，等待负责人确认后 `upload.ps1` 上传

## 7. 手测反馈修复轮（流式输出不渲染，需点击才显示）

- [x] 7.1 前端 `AiAssistantPage.vue`：`reactive(assistantMsg)` 包裹消息对象，流式 delta 恢复触发重渲染；`npm run build` 通过
- [x] 7.2 后端 `RagAskServiceImpl`：`EventSink` 增 `complete()`，sseSink 实现关闭 emitter，零命中/正常 done/LLM error/检索异常四个终止路径均调用；`RagAskServiceImplTest` 4 条 complete 断言通过，全量 186 单测通过
- [x] 7.3 实测验证：SSE 1.99s 完整返回（meta→delta→done）且连接立即关闭（修复前挂 120s 超时）；IssueLog 第 131 行更新为已修复

## 8. 手测反馈修复轮（偶发 embedding Connection reset）

- [x] 8.1 `RagEmbeddingClient` 传输层重试：IOException 自动重试 2 次（300ms/1s 退避），HTTP 层错误不重试；耗尽后报错带端点与重试次数；新增 2 条单测（死连接重试成功 / 耗尽报错）通过，全量 187 单测通过；IssueLog 第 132 行更新为已修复

## 9. 手测反馈修复轮（明细面板时间 NaN + 19.9s 慢请求解释）

- [x] 9.1 前端时间戳归一化：后端 Long 序列化为字符串导致 `new Date("1789…")` 为 Invalid Date（开始/结束显示 NaN）；onDone 构建 trace 时统一 Number()（steps 的 null 保留跳过语义），formatClock 加 Number.isFinite 兜底；构建通过，IssueLog 第 133 行已修复
- [x] 9.2 19.9s 慢请求定性（非代码缺陷）：日志显示同一请求 embed=19913ms 且 firstToken=19669ms（两条独立出站调用同窗口变慢），且无重试告警 → 该时刻机器出站网络整体抖动；16 分钟前同类请求 embed 仅 379ms。重试机制只覆盖"失败"不覆盖"慢"，属网络环境问题，暂不改代码
