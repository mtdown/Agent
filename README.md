<div align="center">

  <h1>云策库 · CloudPolicy Wiki</h1>
  <h3>企业知识库 Agent —— 云协同 · 政策解读 · 智能检索</h3>
  <p><em>以 Wiki 沉淀云数据与内容，以 RAG 打通政策解读对话的智能知识库平台</em></p>

  <img src="https://img.shields.io/badge/Java-11-ED8B00?style=flat&logo=openjdk&logoColor=white" alt="Java 11" />
  <img src="https://img.shields.io/badge/Spring_Boot-2.7.6-6DB33F?style=flat&logo=spring&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/MyBatis--Plus-3.5.9-1E6BB8?style=flat" alt="MyBatis-Plus" />
  <img src="https://img.shields.io/badge/Vue-3.5-4FC08D?style=flat&logo=vuedotjs&logoColor=white" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Ant_Design_Vue-4.2-0170FE?style=flat&logo=antdesign&logoColor=white" alt="Ant Design Vue" />
  <img src="https://img.shields.io/badge/TypeScript-5.6-3178C6?style=flat&logo=typescript&logoColor=white" alt="TypeScript" />
  <img src="https://img.shields.io/badge/MySQL-8-4479A1?style=flat&logo=mysql&logoColor=white" alt="MySQL" />
  <img src="https://img.shields.io/badge/Redis-7-DC382D?style=flat&logo=redis&logoColor=white" alt="Redis" />
  <img src="https://img.shields.io/badge/Sa--Token-1.44-2B5B84?style=flat" alt="Sa-Token" />
  <img src="https://img.shields.io/badge/腾讯云_COS-对象存储-006EFF?style=flat" alt="COS" />

</div>

---

## 项目简介

**云策库（CloudPolicy Wiki）** 是一个面向政策解读与公共办事问答场景的企业知识库平台。平台以 **Wiki 知识库** 沉淀结构化、可协作、可追溯的团队知识资产，并已在其上建成完整的 **RAG（检索增强生成）** 链路——「文档入库 → 切片向量化 → 权限过滤检索 → AI 流式问答」，配套开放式检索 API 与可复现的检索层评测体系。

系统采用前后端分离架构：前端提供双栏 Wiki 工作台（空间树 + 文档列表 + Markdown 编辑器），知识库后端负责空间权限、文档生命周期、附件存储与全文检索，独立 Agent 服务负责流式对话循环和工具调用。

## 演进路线

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| **第一步 · Wiki 知识库** | 多空间（公开/团队/个人）文档沉淀、Markdown 编辑、附件/图片、全文检索、回收站、团队 RBAC 权限 | ✅ 已实现 |
| **第二步 · RAG 检索增强** | 文档切片向量化 → 权限过滤检索（含文号精确匹配层）→ AI 流式问答；开放检索 API 与评测体系 | ✅ 已实现 |
| **第三步 · 检索层优化** | 混合检索与语义重排已实现；证据充分性、拒答与跨语料泛化效果需进一步评测 | 🚧 持续优化 |
| **最终 · 政策解读对话** | 面向政策/公共办事问答的应用方向；回答质量需结合实际语料和评测判断 | 🎯 目标 |

## 功能特性

### 知识库（Wiki）

- **文档编辑**：Markdown 编辑器（CodeMirror 内核），支持粘贴 / 拖拽图片自动上传并插入 Markdown 语法，老纯文本文档无缝兼容。
- **空间体系**：公开空间、团队空间、个人空间三类隔离；团队空间支持成员管理（搜索添加、角色分配 admin/editor）。
- **文件夹树**：左侧导航按「公开/团队/个人」分组 → 空间 → 无限嵌套文件夹；首屏默认展开全部分组。
- **全文检索**：MySQL FULLTEXT + ngram 中文分词检索，替换无法走索引、不分词的 `LIKE '%kw%'`。
- **批量操作**：文档批量移动、批量删除，管理态一键全选。
- **回收站**：逻辑删除 → 回收站恢复 / 物理删除，支持按空间级联处理。

### 文件与图库

- **附件/图片存储**：腾讯云 COS 对象存储，缩略图 + 原图分离（`imageMogr2` 压缩），上传按文件魔数校验防伪装。
- **AI 图片编辑**：在线裁剪/旋转（vue-cropper）与 AI 智能扩图（阿里云百炼，异步轮询任务模型）。
- **协同编辑**：WebSocket + Disruptor 无锁队列，基于业务乐观锁的「同一时刻单编辑者」并发控制。

### AI 问答与检索（RAG）

- **切片与向量化**：Markdown 按标题层级切块（`MarkdownChunker`），经 OpenAI 兼容接口生成向量写入 `wiki_chunk`。
- **权限过滤优先**：检索前先按可见空间集合过滤，非成员对受限空间**零可见**（评测实测 `leakCount = 0`）。
- **文号精确匹配层**：查询含公文文号时先按 `docNumber` 精确命中——纯向量分不开「〔2026〕66 号」与「〔2026〕67 号」。
- **AI 流式问答**：SSE 流式输出（`meta / delta / done / error`），答案句末标注来源角标，思考型与快速模型可切换。
- **开放检索 API**：`POST /open/rag/search`、`/open/rag/ask`，以 `X-API-Key` 替代登录态，复用同一套权限过滤逻辑。
- **链路可观测**：permission / docNumber / embed / vector / firstToken / llm 分步耗时，前端可展开调用明细时间线。

### Agent 与工具

- **独立 Agent 服务**：`cloud_agent/` 提供流式对话循环与工具调用，并与知识库后端交互。
- **多轮对话与历史**：对话可带入先前消息作为上下文；支持新建、查看、重命名和删除历史会话。
- **本地模型档位**：开发界面支持本地 / 云端模型分组选择、新增和删除自定义档位，选择保存在浏览器；标准生产构建默认隐藏。当前选择仅记录本地偏好，后端实际模型仍由启动环境变量决定，尚未随按钮切换。
- **本地密钥管理**：开发界面可将模型密钥写入项目根目录 `.env.local`，档位只保留密钥变量名引用；更新后需重启后端与 Agent。启用临时公网隧道时不注册密钥写入端点，生产构建不提供该入口。
- **Wiki 写入**：Agent 可查询可写位置；目标不明确时请求选择，用户确认写入空间后再创建文件夹和文档，也支持按请求导入网页。
- **Skill 与代码库**：管理可复用 Skill 的版本和启停，以及 Python / Java 代码版本；代码保存即发布，初始状态为未验证，发布或绑定 Skill 不等于执行。
- **联网搜索与抓取**：配置独立的网页 API 密钥后启用；搜索词或 URL 会发送给上游，网页内容进入模型上下文。每轮搜索和抓取各最多三次上游请求（含重试），可能产生费用，详见 [网页能力说明](./cloud_agent/README-web-research.md)。

### 安全与治理

- **双认证域**：Sa-Token 多账户登录，站点主登录态与空间登录态（`StpKit.SPACE`）完全隔离。
- **配置驱动 RBAC**：角色 → 权限映射维护在 JSON 配置，`@SaSpaceCheckPermission` 注解 + Spring AOP 声明式校验。
- **统一异常与响应**：`BaseResponse` 统一响应体、`GlobalExceptionHandler` 全局异常兜底、`@AuthCheck` 角色校验。
- **缓存一致性**：Redis + Caffeine 多级缓存，写操作统一清理缓存入口，避免脏数据。
- **站点部署与备案**：已有宿主机 HTTP 80 → Nginx 容器 8080 的端口映射配置，页面底部展示 ICP 备案号及工信部查询链接；部署和回滚步骤见 [部署说明](./deploy/README.md)。

## RAG 检索与评测

### 检索链路

```text
用户提问
   |
   +-- 1. 权限过滤      可见空间集合 ∩ 请求空间（非成员零可见）
   +-- 2. 文号精确匹配  查询含公文文号时按 docNumber 精确命中
   +-- 3. 混合检索      向量通道（DashScope 1024 维）+ BM25 词法通道（CJK bigram）
   |                    RRF 融合 → 候选池 top-50
   +-- 4. 语义重排      可按配置启用；服务不可用时回退（本文历史评测请求返回前 10 条）
   +-- 5. 答案生成      证据送入 LLM，SSE 流式输出，句末标注来源角标
```

### 历史基线

以下结果来自 [2026-09-16 HTTP 基线文件](./eval/results/baseline-20260916-201335-http.json)，统一采用 `recall@10`、`docRecall@10`、`hitRate@10` 口径，直接读取结果中的对应字段（`fetchK = 10`）。使用当时的语料、模型与配置，不代表当前部署效果；423 题中 399 题计入召回指标。本次仅更新展示口径，没有重跑评测或修改检索默认配置。更换链路、语料或模型后应重新评测并按题型比较。

数据集 `eval/golden.v2.jsonl`（423 题 / 七类题型），http 模式实测真实系统，embedding 为
DashScope `qwen3.7-text-embedding-flash`（1024 维），检索为「混合检索 + 语义重排」。

| 指标 | 值 | 说明 |
| --- | --- | --- |
| `recall@10` | **0.851** | 前 10 条的 chunk 级召回，衡量目标段落覆盖 |
| `docRecall@10` | **0.967** | 前 10 条的文档级召回，跨实验对比需保持同口径 |
| `hitRate@10` | **0.945** | 前 10 条至少命中一个目标段落 |
| `mrr` | **0.626** | 首个正确段落排名倒数的均值，未命中按 0 计 |
| 权限泄漏 | 0 | D 类双账号对照，非成员零泄漏 |

与 [2026-09-15 对比基线](./eval/results/baseline-20260915-091440-http.json) 相比，引入混合检索与语义重排后，`recall@10` 由 **0.639 提升至 0.851（+21.2pt）**，`hitRate@10`
提升 **17.3pt**，`mrr` 由 **0.449 提升至 0.626**。另据[历史优化实验记录](./openspec/changes/archive/2026-09-16-boost-rag-passage-recall/proposal.md)，候选池 50 的单并发延迟实验平均响应 **516ms**（p95 600ms）；该延迟来自单独实验，不是本次 @10 展示更新的重测结果。

分组指标（题型难度分层，跨版本比较必须分组）：

| 类别 | 题数 | `recall@10` | `docRecall@10` |
| --- | --- | --- | --- |
| X 跨文档（新闻/解读提问、答案锚政策原文） | 89 | 0.876 | 0.966 |
| A 配对 | 54 | 0.559 | 0.889 |
| S 单文档 | 235 | 0.902 | 0.983 |
| B 文号 | 9 | 0.843 | 1.000 |

三条最该记住的结论：

1. **文档级与段落级召回的差距被显著收窄**：`docRecall@10` 与 `recall@10` 的落差由 **30.3pt 降至 11.6pt**，支持继续关注段落定位质量。
2. **题型难度分层明显**（单文档 0.902 vs 配对 0.559）→ 混算 overall 会受题型配比影响，跨版本比较必须分组。
3. **文号题 `docRecall@10` 与 `hitRate@10` 均达 1.000** → 规则层精确命中与语义检索形成互补。

关于覆盖度与可复现性（扩题一轮的产出）：

- 文档覆盖 37/216（17%）→ **183/216（85%）**，切片覆盖 15.3% → **27.6%**
- 新题 100% 带可抄录锚点 `quote`，锚点经程序校验必须真实存在于原文，防止标注幻觉
- 2026-09-15 的[第一次](./eval/results/baseline-20260915-091252-http.json)与[第二次](./eval/results/baseline-20260915-091440-http.json)历史重跑中，`recall@10` 分别为 0.641677 与 0.639171，极差约 **0.25 个百分点**；这是当时的观测抖动，不作为所有配置的固定可信阈值，小幅提升需重复评测确认

评测集设计、指标定义与复现步骤见 [`eval/README.md`](./eval/README.md)；该文档保留其历史展示口径，本页的 @10 数值以以上 JSON 为准。

## 技术亮点

### RAG 与 AI 工程

下述评测数字沿用上文历史基线及其对比实验，适用于当时的语料和配置，并非当前部署的实测结果。

1. **七类题型评测集（423 题）**：配对 / 文号 / 无答案 / 权限镜像 / 合成，外加扩题新增的**跨文档**（新闻提问、答案锚政策原文）与**单文档**。gold 锚点用 `docId + chunkIndex` 逻辑坐标而非数据库主键——索引重建是「旧行置 INVALID + 插新行」，绑主键会全部失效；`gold[].quote` 经程序校验必须真实存在于原文，防止标注幻觉。
2. **把评测集本身当工程对象迭代**：先用置信区间量化"考卷够不够用"，再定位根因——出题只在**标题**的书名号里找政策名，159 篇解读类只配上 31 对（128 篇媒体视角只中 4 篇）。放宽到正文匹配后配对数 **31 → 150**，文档覆盖 **17% → 85%**，可分辨样本 **29 → 76**，置信区间收窄到 **±11.2%**；同时保留历史重复运行结果，按同一指标口径核对数值抖动。
3. **文号精确匹配层**：公文文号在纯向量下几乎不可分，加入规则层精确命中后，B 类题 `docRecall@10` 与 `hitRate@10` 均达 **1.000**，与语义检索形成互补。
4. **混合检索 + 语义重排**：向量通道（DashScope 1024 维）+ BM25 词法通道（CJK bigram）经 RRF 融合扩出 **top-50** 候选池，再由 `qwen3.7-text-rerank` 精排；本文历史评测请求前 10 条。`recall@10` 由 **0.639 升至 0.851**；独立的历史延迟实验平均 **516ms**（p95 600ms），来源见上文。
5. **权限过滤零泄漏验证**：D 类题用成员 / 非成员双 API Key 对照，非成员 `leakCount = 0`，权限过滤在检索前生效。
6. **从指标诊断到落地优化的闭环**：对比基线中 `docRecall@10` 高于 `recall@10`；优化后二者落差由 **30.3pt 收窄至 11.6pt**，为段落定位方向的优化提供依据。

### 平台工程

7. **WebSocket 协同编辑**：`PictureEditHandler` 收到消息即封装事件发布到 Disruptor RingBuffer，网络 I/O 线程与业务线程解耦，实现高吞吐的消息处理。
8. **Sa-Token 多账户认证域**：为团队空间建立独立 `StpKit.SPACE` 认证域，与主登录态互不干扰，解决 B 端「在特定空间是什么角色」的授权问题。
9. **雪花 ID 精度修复**：`JsonConfig` 将返回的 `Long` 统一序列化为 `String`，根治 JavaScript `Number` 无法精确表示 19 位雪花 ID（`2^53`）导致的前端丢精度。
10. **逻辑删除 + 回收站**：手写 SQL 绕过 MyBatis-Plus 全局逻辑删除过滤，实现恢复/物理删除链路。
11. **N+1 查询治理**：文档列表批量映射作者（`listByIds` 一次查询），替换逐条查作者。
12. **前后端 API 自动化**：后端 Knife4j 生成 OpenAPI JSON，前端 `@umijs/openapi` 自动生成带类型的请求函数与类型定义，int64 统一映射 `string | number`。
13. **冷启动注册缺陷修复**：sa-token 注解常量内联不触发 `StpKit` 类加载，`StpKitRegisterConfig` 启动期 `@PostConstruct` 显式注册 `StpLogic`。
14. **本地模型与密钥入口**：本地 / 云端档位分组管理、浏览器偏好持久化，密钥变量名引用与明文存储分离；开发期写入根 `.env.local`，启动脚本与评测加载链共用，生产环境独立配置。
15. **部署访问与备案展示**：宿主机入口端口与容器监听端口分别配置，页面统一展示备案链接；本地公网隧道显式开启，并在开启时关闭开发密钥写入入口。

## 技术架构

| 层级 | 技术栈 |
| --- | --- |
| 前端 | Vue 3.5 + Vite 6 + TypeScript 5.6 + Ant Design Vue 4.2 + Pinia + Vue Router |
| 后端 | Spring Boot 2.7.6 + Java 11 + MyBatis-Plus 3.5.9 |
| Agent 服务 | 独立 Spring Boot 服务，负责流式对话循环和工具调用 |
| 权限认证 | Sa-Token 1.44（多账户认证域 + AOP 声明式校验） |
| 数据库 | MySQL 8（逻辑删除、FULLTEXT 全文索引） |
| 缓存 | Redis 7（分布式缓存 / Session）+ Caffeine 3.1（本地缓存） |
| 检索增强（RAG） | Markdown 切块、权限过滤、文号匹配、向量与 BM25 混合检索（RRF 融合），语义重排可配置 |
| 大模型接入 | OpenAI 兼容接口（DashScope / DeepSeek），SSE 流式输出，思考型与快速模型可切换 |
| 对象存储 | 腾讯云 COS（图片/附件，缩略图压缩） |
| 接口文档 | Knife4j 4.4（OpenAPI 2） + @umijs/openapi 代码生成 |
| 工具库 | Hutool、commons-lang3、Jsoup |

## 项目结构

```text
├── cloud/                       # 后端 Spring Boot
│   ├── src/main/java/com/et/cloud/
│   │   ├── annotation/          # 自定义注解（@AuthCheck / @SaSpaceCheckPermission）
│   │   ├── commen/              # 统一响应体 BaseResponse / ResultUtils
│   │   ├── config/              # Cors / Json（Long→String）/ MyBatis-Plus / Sa-Token / 初始化器
│   │   ├── controller/          # DocumentWiki / WikiSpace / WikiFolder / WikiRecycle / Picture / Space / User ...
│   │   ├── dto/                 # 请求 DTO
│   │   ├── enums/               # 枚举（SpaceType / SpaceRole ...）
│   │   ├── exception/           # ErrorCode / BusinessException / GlobalExceptionHandler / ThrowUtils
│   │   ├── manager/             # 缓存管理、通用能力
│   │   ├── mapper/              # MyBatis-Plus Mapper（含手写 SQL 绕过逻辑删除）
│   │   ├── model/               # entity 实体 / vis 视图对象
│   │   ├── rag/                 # RAG 链路（切片 / 检索 / 问答 / 开放 API Key / 索引管理）
│   │   ├── service/             # 业务逻辑（含 impl）
│   │   └── websocket/           # WebSocket + Disruptor 协同编辑
│   ├── sql/                     # 建表 / 补列 / 全文索引 / 数据订正脚本
│   └── pom.xml
├── cloud_agent/                 # 独立 Agent 服务（对话循环 / 工具调用）
├── cloud_front/                 # 前端 Vue 3 + Vite
│   ├── src/
│   │   ├── api/                 # @umijs/openapi 生成的接口与类型定义
│   │   ├── pages/               # documentWiki（Wiki 工作台）/ admin / user / 图库页面
│   │   ├── components/          # WikiSpaceTree / WikiDocumentList / DocumentWikiEditor 等
│   │   ├── stores/              # Pinia 全局状态（登录态）
│   │   ├── router/              # 路由 + access.ts 前端权限守卫
│   │   ├── request.ts           # axios 封装（统一拦截 / 错误处理）
│   │   └── constants/           # 常量（空间类型等）
│   ├── openapi.config.js        # OpenAPI 代码生成配置
│   └── package.json
├── eval/                        # RAG 评测集与评测脚本（423 题 / 七类题型 / 结果与报告）
├── docs/                        # 文档与归档
│   ├── asset-registry.md        # 无版本保护资产登记（删了无法恢复的东西在这里查）
│   ├── history/                 # 历史台账归档（旧变更台账 / 旧问题记录表 / 后端结构图）
│   └── legacy-notes/            # 旧项目学习笔记归档（图库 → Wiki 演进记录，含配图）
├── openspec/                    # OpenSpec 需求与执行管理（changes / specs）
├── start-dev.ps1 / stop-dev.ps1 # 本地开发服务启停脚本
└── scripts/                     # 辅助脚本与 SQLite 问题跟踪工具
```

### 文档指引

| 想了解 | 看哪里 |
|---|---|
| 当前系统能力与基线 | 本文件「功能特性」「RAG 检索与评测」 |
| 某项改动为什么这么做 | `openspec/changes/`（在建）与 `openspec/changes/archive/`（已归档，含完整 proposal / design / 实测数据） |
| 模型档位、本地密钥与部署方式 | [模型档位说明](./openspec/changes/archive/2026-11-05-add-model-selector/README.md)、[部署说明](./deploy/README.md) |
| 开发角色分工与验收要求 | [共同规则](./AGENTS.md)、[总管规则](./AGENTS_manager.md)、[开发规则](./AGENTS_rules.md)、[测试规则](./AGENTS_test.md) |
| 哪些文件没有版本保护、丢了无法恢复 | `docs/asset-registry.md` |
| 早期项目（图库 → Wiki）的学习笔记与历史台账 | `docs/legacy-notes/`、`docs/history/` |

> 根目录在 2026-09 做过一次文档分代整理：跨项目的旧笔记与已停更的台账整体迁入 `docs/`，
> 迁移保持原有相对路径，因此旧笔记内的图片引用仍然有效。

## 快速开始

### 环境要求与配置

- Windows PowerShell、Java 11+、Maven、Node.js 18+ 和 npm
- MySQL 8、Redis 7；启动脚本检查本机 MySQL **3307** 和 Redis **6379**，未监听时警告。先启动依赖服务并按数据库状态执行 `cloud/sql/` 下所需脚本。
- 一键启动脚本以 `local` profile 启动后端；[基础配置](./cloud/src/main/resources/application.yml)中的数据库和 Redis 地址不一定对应脚本检查端口，请核对本地覆盖配置。
- 按使用的功能配置数据库、Redis、对象存储及模型凭据，参考 [后端配置](./cloud/src/main/resources/application.yml)、[Agent 配置](./cloud_agent/src/main/resources/application.yml) 和 [部署说明](./deploy/README.md)。模型密钥统一存放在 Git 忽略的根目录 `.env.local`，启动脚本读取其中非空的 `RAG_*` / `DEEPSEEK_*` 项注入进程环境，请勿提交密钥；未配置 Agent 内部密钥时脚本生成本次运行的临时密钥。
- 本地开发可在模型管理界面更新密钥，写入后重启后端与 Agent；标准生产构建默认隐藏模型选择器且不提供密钥写入端点。云端使用独立的部署凭据，与本地只统一变量名，不自动同步密钥值。

首次使用前安装前端依赖：

```powershell
cd cloud_front
npm install
cd ..
```

### 1. 启动后端

```bash
cd cloud
# 首次需执行 cloud/sql/ 下的建表脚本
mvn spring-boot:run
```

后端启动后接口文档地址：`http://127.0.0.1:8123/api/doc.html`。如使用本地覆盖配置，可指定 `-Dspring-boot.run.profiles=local`。

### 2. 启动前端

```bash
cd cloud_front
npm install
npm run dev
```

### 3. 启动 Agent 服务

完整 Agent 对话需要独立启动 `cloud_agent/`；手动启动时，后端与 Agent 进程需配置相同的 `AGENT_INTERNAL_KEY`，并按使用的功能配置模型凭据。可在分别配置好环境变量的终端中运行：

```bash
cd cloud_agent
mvn spring-boot:run
```

### 4. 本地开发一键启停

在项目根目录依次运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
```

脚本构建并启动知识库后端（8123）、Agent（8124）和前端（3000），日志位于 `tmp/`。临时公网隧道默认关闭；需要时给 `start-dev.ps1` 加 `-Tunnel` 显式开启 cloudflared，未安装时仅局域网访问。保留 `-NoTunnel`，与 `-Tunnel` 同时指定时优先关闭隧道。按回车停止全部服务和隧道；关闭窗口后可用 `stop-dev.ps1` 停止。`-Preview` 构建并预览前端，`-NoPause` 跳过等待回车。

## 访问地址

| 服务 | 地址 |
| --- | --- |
| 前端 | http://127.0.0.1:3000 |
| 后端 API | http://127.0.0.1:8123/api |
| Agent 服务 | http://127.0.0.1:8124 |
| 接口文档 | http://127.0.0.1:8123/api/doc.html |
| 健康检查 | http://127.0.0.1:8123/api/health |

## 默认端口

| 服务 | 端口 |
| --- | --- |
| 前端（Vite） | 3000 |
| 后端（Spring Boot） | 8123 |
| Agent 服务 | 8124 |
| MySQL（启动脚本检查） | 3307 |
| Redis | 6379 |

## 常用命令

```bash
# 后端
cd cloud
mvn compile          # 编译
mvn test             # 测试（建议 -Dspring.profiles.active=local）
mvn -DskipTests package  # 打包

# Agent
cd ../cloud_agent
mvn compile
mvn test
mvn -DskipTests package

# 前端
cd ../cloud_front
npm run dev          # 开发
npm run type-check   # 类型检查
npm run build        # 构建（类型检查 + 打包）
npm run openapi      # 根据后端 api-docs 重新生成接口代码
```

## 开发规范

本项目采用 [OpenSpec](./openspec/) 管理需求与执行流程，分支命名、协作和验收规则详见 [AGENTS.md](./AGENTS.md)。新问题当前状态由本机 SQLite 的 `scripts/issue_tracker.py` 管理，操作及备份见 [问题跟踪说明](./scripts/README-issue-tracker.md)。

| 开发角色 | 模型 | 推理强度 | 职责 |
| --- | --- | --- | --- |
| 主智能体／总管 | GPT-6.2 Sol | High（高） | 规划、派单、核对证据与交付；权限模型变更、数据库迁移或跨系统契约冲突的设计阶段可选 GPT-6.2 Astra High |
| 开发子智能体 | GPT-6.2 Sol | Medium（中） | 按任务卡实施、自检与交接；纯文档整理或可机械核验的批量替换在工具支持时可选 GPT-6 Luna Low，并发、缓存一致性或跨模块排错可选 GPT-6.2 Sol High |
| 独立测试子智能体 | GPT-6.2 Sol | Medium（中） | 在独立上下文按固定验收验证；主要依靠截图或桌面操作判断结果时可选 GPT-6.1 Terra Medium |
| 独立只读复核 | 按现行复核条件另派实例 | 不设默认 | 验收证据冲突或有争议的高风险复核可选 GPT-6.2 Astra High；保留原始测试结论，批语不替代测试 |

角色选型以 [AGENTS.md](./AGENTS.md) 共同表为准，常规工作优先 Standard 速度；总管模型由启动者在启动会话时选择，文档不自动切换当前会话。总管按当前工具实际支持的组合创建开发、测试与复核实例，显式指定 `model`、`reasoning_effort` 和 `fork_turns="none"`，不继承总管配置；Luna 仅在工具明确支持时可选，所选组合不可用时停止该派发并上报，不自动替换。修改后的新一轮测试仍需新独立实例、冻结快照和独立证据。上述为开发协作模型，应用内模型档位另行配置。新任务先检查工作区，再用 `start-task.ps1` 建立任务分支和 OpenSpec 变更；按阶段开发、冻结快照与验收，收尾同步规格并归档，通过 `upload.ps1` 推送，合并 main 需要负责人明确授权。

## 路线图

- [x] Wiki 知识库：多空间、Markdown 编辑、全文检索、回收站、团队 RBAC
- [x] RAG 检索增强：文档切片向量化 → 权限过滤检索（含文号精确匹配层）→ AI 流式问答
- [x] 检索层评测体系：七类题型数据集（423 题，文档覆盖 85%）、双模式 runner、分组指标报告、跨版本对比与可复现性量化
- [x] 开放检索 API：`X-API-Key` 鉴权的外部只读接口
- [x] 检索层优化：混合检索与可配置语义重排
- [x] Agent 服务：流式对话、工具调用、Wiki 目标选择及确认后写入、Skill 和代码库
- [x] 本地模型档位与密钥管理：浏览器偏好持久化、统一 `.env.local` 入口、开发与生产隔离
- [x] 部署访问与备案展示：HTTP 80 入口映射、页脚备案链接、临时公网隧道显式开启
- [ ] 将界面所选模型档位接入后端实际请求；目前仍由启动环境变量决定
- [ ] 持续评测证据充分性、拒答与跨语料泛化效果
- [ ] 政策解读对话：面向政策/公共办事问答持续验证效果

---

<em>从 Wiki 知识沉淀到政策解读对话 —— 云策库，让政策知识可被检索、可被追问、可被复用。</em>
