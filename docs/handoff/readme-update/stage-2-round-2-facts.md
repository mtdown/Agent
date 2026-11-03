# 阶段 2 第 2 轮开发事实
基线 bc93ed189fc98c3580a583b2d39f8f3635c6cf63；实施文件仅 README.md。开发者报告恢复独立后端和前端启动示例、Node.js 18+，补充 Agent 手动启动及配置。开发者报告 git diff、命令检索、git diff --check 和 UTF-8 链接检查退出 0（missing: []）；废弃变量检索退出 1；未启动服务或重跑评测。问题 29 已更新待验证。

基线删改逐组依据（开发者提供）：
1. 简介后端职责细化并增加 Agent：cloud_agent/pom.xml、AgentController.java。
2. 演进及路线图状态原先冲突；RagSearchServiceImpl.java、RagProperties.java 支持混合与可配置重排，效果未重测。
3. 检索链路固定重排改为配置及回退：RagProperties.java、RagSearchServiceImpl.java；保留历史 top-50/top-6。
4. 当前基线改为历史：eval/README.md 和 baseline-20260916-201335-http.json 记录当时条件。
5. 技术架构检索描述补充 BM25 和 RRF：LexicalIndex.java、RrfFusion.java、RagSearchServiceImpl.java。
6. 问题入口替换：AGENTS.md、scripts/README-issue-tracker.md 指定本机 SQLite；旧表仅历史查询。
7. 环境版本及手动后端前端命令已恢复。
8. 一键启动改依次 stop/start：固定验收 1.2 A1；start-dev.ps1 有端口清理与默认隧道；因新增 Agent 小节改变编号。
9. 后端地址增加 /api：cloud/src/main/resources/application.yml 的 context-path；MySQL 数值明确为脚本检查端口，见 start-dev.ps1。
10. 常用命令追加 Agent 后调整前端相对目录以匹配当前工作目录。
