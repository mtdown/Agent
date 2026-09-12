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

**云策库（CloudPolicy Wiki）** 是一个面向政策解读与公共办事问答场景的企业知识库平台。当前阶段以 **Wiki 知识库** 为核心，将零散的云数据与内容沉淀为结构化、可协作、可追溯的团队知识资产；下一步将基于 **RAG（检索增强生成）** 打通「上传文本 → 知识库 → AI 检索」的闭环，最终落地为**政策解读对话智能体**。

系统采用前后端分离架构：前端提供双栏 Wiki 工作台（空间树 + 文档列表 + Markdown 编辑器），后端负责空间权限、文档生命周期、附件存储与全文检索。

## 三步愿景

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| **第一步 · Wiki 知识库** | 多空间（公开/团队/个人）文档沉淀、Markdown 编辑、附件/图片、全文检索、回收站、团队 RBAC 权限 | ✅ 已实现 |
| **第二步 · RAG 检索增强** | 上传文本 → 切片入库 → 向量/全文检索 → 供 AI 生成政策解读答案 | 🚧 规划中 |
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

### 安全与治理

- **双认证域**：Sa-Token 多账户登录，站点主登录态与空间登录态（`StpKit.SPACE`）完全隔离。
- **配置驱动 RBAC**：角色 → 权限映射维护在 JSON 配置，`@SaSpaceCheckPermission` 注解 + Spring AOP 声明式校验。
- **统一异常与响应**：`BaseResponse` 统一响应体、`GlobalExceptionHandler` 全局异常兜底、`@AuthCheck` 角色校验。
- **缓存一致性**：Redis + Caffeine 多级缓存，写操作统一清理缓存入口，避免脏数据。

## 技术亮点

1. **WebSocket 协同编辑**：`PictureEditHandler` 收到消息即封装事件发布到 Disruptor RingBuffer，网络 I/O 线程与业务线程解耦，纳秒级延迟、百万级吞吐。
2. **Sa-Token 多账户认证域**：为团队空间建立独立 `StpKit.SPACE` 认证域，与主登录态互不干扰，解决 B 端「在特定空间是什么角色」的授权问题。
3. **雪花 ID 精度修复**：`JsonConfig` 将返回的 `Long` 统一序列化为 `String`，根治 JavaScript `Number` 无法精确表示 19 位雪花 ID（`2^53`）导致的前端丢精度。
4. **逻辑删除 + 回收站**：手写 SQL 绕过 MyBatis-Plus 全局逻辑删除过滤，实现恢复/物理删除链路。
5. **N+1 查询治理**：文档列表批量映射作者（`listByIds` 一次查询），替换逐条查作者。
6. **前后端 API 自动化**：后端 Knife4j 生成 OpenAPI JSON，前端 `@umijs/openapi` 自动生成带类型的请求函数与类型定义，int64 统一映射 `string | number`。
7. **冷启动注册缺陷修复**：sa-token 注解常量内联不触发 `StpKit` 类加载，`StpKitRegisterConfig` 启动期 `@PostConstruct` 显式注册 `StpLogic`。

## 技术架构

| 层级 | 技术栈 |
| --- | --- |
| 前端 | Vue 3.5 + Vite 6 + TypeScript 5.6 + Ant Design Vue 4.2 + Pinia + Vue Router |
| 后端 | Spring Boot 2.7.6 + Java 11 + MyBatis-Plus 3.5.9 |
| 权限认证 | Sa-Token 1.44（多账户认证域 + AOP 声明式校验） |
| 数据库 | MySQL 8（逻辑删除、FULLTEXT 全文索引） |
| 缓存 | Redis 7（分布式缓存 / Session）+ Caffeine 3.1（本地缓存） |
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
├── openspec/                    # OpenSpec 需求与执行管理（changes / specs）
├── start-dev.ps1 / stop-dev.ps1 # 本地开发服务启停脚本
├── IssueLog.xlsx                # 问题日志（按任务记录报错/阻塞/解决方案）
└── README_old.md                # 学习笔记（从图库到 Wiki 的完整演进记录）
```

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
- [ ] RAG 检索增强：上传文本 → 切片 → 向量/全文检索 → 供 AI 生成答案
- [ ] 政策解读对话：面向政策/公共办事问答的智能体落地

---

<em>从 Wiki 知识沉淀到政策解读对话 —— 云策库，让政策知识可被检索、可被追问、可被复用。</em>
