## 1. 规则与展示文档更新

负责人授权：本次由主智能体独立完成全部修改与检查，不创建子智能体。该次例外不改变日常独立验收要求；下列完成状态指文档自检完成，不代表独立测试 PASS。L0 省略 design.md 与 specs delta，理由见 proposal.md。执行顺序 1.1 → 1.2 → 1.3；预计 7 个文件（3 个现有文档、3 个 OpenSpec 工件、1 份验证报告），不涉及产品契约、权限或数据修改。

- [x] 1.1 固定角色模型并约束派发。允许 AGENTS.md、AGENTS_manager.md；verify：`rg -n 'gpt-6-astra|gpt-6.1-sol|high|medium|fork_turns|不可用' AGENTS.md AGENTS_manager.md`。预期：总管 gpt-6-astra/high，开发与测试 gpt-6.1-sol/medium；子实例明确 fork_turns=none，完整任务卡传递上下文；不可用时上报，不擅自替换；不声称文档会切换当前会话模型。
- [x] 1.2 沿用 README 格式更新近期开发内容。允许 README.md，依赖 1.1；verify：对照 `git show a8784092ed583b76cade5e80f159d70b312d8fc0:README.md` 的二级标题顺序及徽章，并核对 `cloud_front/src/components/ModelSelector.vue`、`cloud_front/src/constants/modelProfiles.ts`、`start-dev.ps1`、`deploy/docker-compose.yml`、`cloud_front/src/layouts/BasicLayout.vue`。预期：标题与徽章保留；写清模型选择仅本地偏好、后端仍使用启动配置，根 .env.local 密钥入口与重启要求，隧道显式开启、备案和端口已有实现；不宣称本次上线或重测。
- [x] 1.3 改用 @10 评测口径并逐项校验。允许 README.md，依赖 1.2；verify：用 Python 读取 `eval/results/baseline-20260916-201335-http.json` 的 overall 与 byCategory，对照 README 的三位小数；对比 `baseline-20260915-091440-http.json`、`baseline-20260915-091252-http.json` 计算提升及历史重跑极差；执行 `rg -n '@6|@recall6' README.md`、`git diff --check`，并用 Python 严格 UTF-8 解码和检查相对链接。预期：@6 无匹配；总表、分组与结论均使用真实 @10 值；历史适用范围明确；链接无缺失、差异检查退出 0。完整命令和结果保存到 `docs/handoff/update-model-rules-readme/verification.md`。

## 回滚与收尾

基线 `a8784092ed583b76cade5e80f159d70b312d8fc0`，任务分支 `feature/model-rules-readme开发`。回滚仅限三份实施文档，先核对归属，再执行 `git restore --source=a8784092ed583b76cade5e80f159d70b312d8fc0 -- AGENTS.md AGENTS_manager.md README.md`；若已提交则另建回滚提交，保留证据。无数据库或服务回滚。

执行 `openspec validate update-model-rules-readme --strict`；无 delta 无需同步主规格。完成自检后按本次负责人单人执行授权归档，再核对提交清单并用 upload.ps1 推送任务分支，不合并 main。未启动服务、未重跑模型评测、未执行独立测试需如实记录。未发现问题时无需空问题页；不创建临时目录。

阶段结论：1.1、1.2、1.3 均完成本人静态核验；完整依据、检查命令、摘要与限制见 `docs/handoff/update-model-rules-readme/verification.md`。按负责人本次单人执行授权收尾，无独立测试 PASS 声明。
