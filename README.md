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

系统采用前后端分离架构：前端提供双栏 Wiki 工作台（空间树 + 文档列表 + Markdown 编辑器），后端负责空间权限、文档生命周期、附件存储与全文检索。

## 演进路线

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| **第一步 · Wiki 知识库** | 多空间（公开/团队/个人）文档沉淀、Markdown 编辑、附件/图片、全文检索、回收站、团队 RBAC 权限 | ✅ 已实现 |
| **第二步 · RAG 检索增强** | 文档切片向量化 → 权限过滤检索（含文号精确匹配层）→ AI 流式问答；开放检索 API 与评测体系 | ✅ 已实现 |
| **第三步 · 检索层优化** | 精排 rerank、证据充分性判定与拒答、跨语料泛化性检验 | 🚧 规划中 |
| **最终 · 政策解读对话** | 面向政策/公共办事问答的智能体，基于知识库给出可溯源、结构化的解答 | 🎯 目标 |

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

### 安全与治理

- **双认证域**：Sa-Token 多账户登录，站点主登录态与空间登录态（`StpKit.SPACE`）完全隔离。
- **配置驱动 RBAC**：角色 → 权限映射维护在 JSON 配置，`@SaSpaceCheckPermission` 注解 + Spring AOP 声明式校验。
- **统一异常与响应**：`BaseResponse` 统一响应体、`GlobalExceptionHandler` 全局异常兜底、`@AuthCheck` 角色校验。
- **缓存一致性**：Redis + Caffeine 多级缓存，写操作统一清理缓存入口，避免脏数据。

## RAG 检索与评测

### 检索链路

```text
用户提问
   |
   +-- 1. 权限过滤      可见空间集合 ∩ 请求空间（非成员零可见）
   +-- 2. 文号精确匹配  查询含公文文号时按 docNumber 精确命中
   +-- 3. 混合检索      向量通道（DashScope 1024 维）+ BM25 词法通道（CJK bigram）
   |                    RRF 融合 → 候选池 top-50
   +-- 4. 语义重排      候选池 50 条 → qwen3.7-text-rerank 精排 → 截断 top-6
   +-- 5. 答案生成      证据送入 LLM，SSE 流式输出，句末标注来源角标
```

### 当前基线

数据集 `eval/golden.v2.jsonl`（423 题 / 七类题型），http 模式实测真实系统，embedding 为
DashScope `qwen3.7-text-embedding-flash`（1024 维），检索为「混合检索 + 语义重排」。

| 指标 | 值 | 说明 |
| --- | --- | --- |
| `recall@6` | **0.793** | chunk 级召回，段落定位准确度 |
| `docRecall@6` | **0.922** | 文档级召回，跨实验对比首选 |
| `hitRate@6` | **0.905** | 前 6 条至少命中一个目标段落 |
| `mrr` | **0.626** | 正确段落平均排在第 1.6 位 |
| 权限泄漏 | 0 | D 类双账号对照，非成员零泄漏 |

引入混合检索与语义重排后，`recall@6` 由 **0.562 提升至 0.793（+23.1pt）**，`hitRate@6`
提升 **22.1pt**，`mrr` 由「平均第 2.2 位」推进到 **1.6 位**；单并发平均响应 **516ms**（p95 600ms）。

分组指标（题型难度分层，跨版本比较必须分组）：

| 类别 | 题数 | `recall@6` | `docRecall@6` |
| --- | --- | --- | --- |
| X 跨文档（新闻/解读提问、答案锚政策原文） | 89 | 0.809 | 0.899 |
| A 配对 | 54 | 0.439 | 0.778 |
| S 单文档 | 235 | 0.864 | 0.957 |
| B 文号 | 9 | 0.685 | 1.000 |

三条最该记住的结论：

1. **文档级与段落级召回的差距被显著收窄**：`docRecall@6` 与 `recall@6` 的落差由 **30.7pt 降至 12.9pt**，说明优化真正作用在段落定位，而不只是把文档捞回来。
2. **题型难度分层明显**（单文档 0.864 vs 配对 0.439）→ 混算 overall 会被题型配比直接污染，跨版本比较必须分组。
3. **文号题 `docRecall@6` 与 `hitRate@6` 均达 1.000** → 公文文号在纯向量下几乎不可分，规则层精确命中与语义检索形成互补。

关于覆盖度与可复现性（扩题一轮的产出）：

- 文档覆盖 37/216（17%）→ **183/216（85%）**，切片覆盖 15.3% → **27.6%**
- 新题 100% 带可抄录锚点 `quote`，锚点经程序校验必须真实存在于原文，防止标注幻觉
- 连续两次运行 `recall@6` 极差 **0.13 个百分点**，即本评测的数值可信下限：更小的差异不作为改进证据

评测集设计、指标口径与复现步骤见 [`eval/README.md`](./eval/README.md)。

## 技术亮点

### RAG 与 AI 工程

1. **七类题型评测集（423 题）**：配对 / 文号 / 无答案 / 权限镜像 / 合成，外加扩题新增的**跨文档**（新闻提问、答案锚政策原文）与**单文档**。gold 锚点用 `docId + chunkIndex` 逻辑坐标而非数据库主键——索引重建是「旧行置 INVALID + 插新行」，绑主键会全部失效；`gold[].quote` 经程序校验必须真实存在于原文，防止标注幻觉。
2. **把评测集本身当工程对象迭代**：先用置信区间量化"考卷够不够用"，再定位根因——出题只在**标题**的书名号里找政策名，159 篇解读类只配上 31 对（128 篇媒体视角只中 4 篇）。放宽到正文匹配后配对数 **31 → 150**，文档覆盖 **17% → 85%**，可分辨样本 **29 → 76**，置信区间收窄到 **±11.2%**；并额外量化出「同数据重跑抖动 0.13 个百分点」作为**数值可信下限**。
3. **文号精确匹配层**：公文文号在纯向量下几乎不可分，加入规则层精确命中后，B 类题 `docRecall@6` 与 `hitRate@6` 均达 **1.000**，与语义检索形成互补。
4. **混合检索 + 语义重排**：向量通道（DashScope 1024 维）+ BM25 词法通道（CJK bigram）经 RRF 融合扩出 **top-50** 候选池，再由 `qwen3.7-text-rerank` 精排截断 top-6。`recall@6` 由 **0.562 升至 0.793**，同时在单并发下保持平均 **516ms** 响应（p95 600ms）。
5. **权限过滤零泄漏验证**：D 类题用成员 / 非成员双 API Key 对照，非成员 `leakCount = 0`，权限过滤在检索前生效。
6. **从指标诊断到落地优化的闭环**：基线显示 `docRecall@6` 远高于 `recall@6`，据此判定瓶颈在段落定位而非文档召回；优化后二者落差由 **30.7pt 收窄至 12.9pt**，验证了诊断方向。

### 平台工程

7. **WebSocket 协同编辑**：`PictureEditHandler` 收到消息即封装事件发布到 Disruptor RingBuffer，网络 I/O 线程与业务线程解耦，实现高吞吐的消息处理。
8. **Sa-Token 多账户认证域**：为团队空间建立独立 `StpKit.SPACE` 认证域，与主登录态互不干扰，解决 B 端「在特定空间是什么角色」的授权问题。
9. **雪花 ID 精度修复**：`JsonConfig` 将返回的 `Long` 统一序列化为 `String`，根治 JavaScript `Number` 无法精确表示 19 位雪花 ID（`2^53`）导致的前端丢精度。
10. **逻辑删除 + 回收站**：手写 SQL 绕过 MyBatis-Plus 全局逻辑删除过滤，实现恢复/物理删除链路。
11. **N+1 查询治理**：文档列表批量映射作者（`listByIds` 一次查询），替换逐条查作者。
12. **前后端 API 自动化**：后端 Knife4j 生成 OpenAPI JSON，前端 `@umijs/openapi` 自动生成带类型的请求函数与类型定义，int64 统一映射 `string | number`。
13. **冷启动注册缺陷修复**：sa-token 注解常量内联不触发 `StpKit` 类加载，`StpKitRegisterConfig` 启动期 `@PostConstruct` 显式注册 `StpLogic`。

## 技术架构

| 层级 | 技术栈 |
| --- | --- |
| 前端 | Vue 3.5 + Vite 6 + TypeScript 5.6 + Ant Design Vue 4.2 + Pinia + Vue Router |
| 后端 | Spring Boot 2.7.6 + Java 11 + MyBatis-Plus 3.5.9 |
| 权限认证 | Sa-Token 1.44（多账户认证域 + AOP 声明式校验） |
| 数据库 | MySQL 8（逻辑删除、FULLTEXT 全文索引） |
| 缓存 | Redis 7（分布式缓存 / Session）+ Caffeine 3.1（本地缓存） |
| 检索增强（RAG） | 自研链路：Markdown 切块 + OpenAI 兼容 embedding + 内存向量余弦检索 + 文号精确匹配层 |
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
└── IssueLog.xlsx                # 问题日志（按任务记录报错/阻塞/解决方案）
```

### 文档指引

| 想了解 | 看哪里 |
|---|---|
| 当前系统能力与基线 | 本文件「功能特性」「RAG 检索与评测」 |
| 某项改动为什么这么做 | `openspec/changes/`（在建）与 `openspec/changes/archive/`（已归档，含完整 proposal / design / 实测数据） |
| 哪些文件没有版本保护、丢了无法恢复 | `docs/asset-registry.md` |
| 早期项目（图库 → Wiki）的学习笔记与历史台账 | `docs/legacy-notes/`、`docs/history/` |

> 根目录在 2026-09 做过一次文档分代整理：跨项目的旧笔记与已停更的台账整体迁入 `docs/`，
> 迁移保持原有相对路径，因此旧笔记内的图片引用仍然有效。

## 快速开始

### 环境要求

- Java 11+
- Node.js 18+
- MySQL 8
- Redis 7

### 1. 启动后端

```bash
cd cloud
# 首次需执行 cloud/sql/ 下的建表脚本
mvn spring-boot:run
```

后端启动后接口文档地址：`http://127.0.0.1:8123/api/doc.html`

### 2. 启动前端

```bash
cd cloud_front
npm install
npm run dev
```

### 3. 本地开发一键启停

项目根目录提供一键启动脚本（默认开启 cloudflared 公网隧道并在控制台展示公网地址；启动前自动清理遗留服务，无需先手动停止）：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
```

启动完成后按回车即停止全部服务（后端 + 前端 + 隧道）；直接关闭窗口则服务保留，之后可用 `stop-dev.ps1` 回收。仅局域网访问时加 `-NoTunnel`。

## 访问地址

| 服务 | 地址 |
| --- | --- |
| 前端 | http://127.0.0.1:3000 |
| 后端 API | http://127.0.0.1:8123 |
| 接口文档 | http://127.0.0.1:8123/api/doc.html |
| 健康检查 | http://127.0.0.1:8123/api/health |

## 默认端口

| 服务 | 端口 |
| --- | --- |
| 前端（Vite） | 3000 |
| 后端（Spring Boot） | 8123 |
| MySQL | 3306 |
| Redis | 6379 |

## 常用命令

```bash
# 后端
cd cloud
mvn compile          # 编译
mvn test             # 测试（建议 -Dspring.profiles.active=local）
mvn -DskipTests package  # 打包

# 前端
cd cloud_front
npm run dev          # 开发
npm run type-check   # 类型检查
npm run build        # 构建（类型检查 + 打包）
npm run openapi      # 根据后端 api-docs 重新生成接口代码
```

## 开发规范

本项目采用 OpenSpec 管理需求与执行流程，分支命名遵循 `feature/xxx开发` / `fix/xxx修复`，任务全程记录到根目录 `IssueLog.xlsx`。详见 [AGENTS.md](./AGENTS.md)。

## 路线图

- [x] Wiki 知识库：多空间、Markdown 编辑、全文检索、回收站、团队 RBAC
- [x] RAG 检索增强：文档切片向量化 → 权限过滤检索（含文号精确匹配层）→ AI 流式问答
- [x] 检索层评测体系：七类题型数据集（423 题，文档覆盖 85%）、双模式 runner、分组指标报告、跨版本对比与可复现性量化
- [x] 开放检索 API：`X-API-Key` 鉴权的外部只读接口
- [x] 检索层优化：精排 rerank、证据充分性判定与拒答、跨语料泛化性检验
- [x] 政策解读对话：面向政策/公共办事问答的智能体落地

---

<em>从 Wiki 知识沉淀到政策解读对话 —— 云策库，让政策知识可被检索、可被追问、可被复用。</em>
