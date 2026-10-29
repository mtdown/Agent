# AI 协作开发规则

## 共同规则（所有角色）

本文件的共同规则适用于所有角色。总管开始协调时必须读取 [AGENTS_manager.md](AGENTS_manager.md)；派单时必须明确要求开发者读取 [AGENTS_rules.md](AGENTS_rules.md)，要求每轮测试者读取 [AGENTS_test.md](AGENTS_test.md)。角色专属职责不要求其他角色履行。三个角色共享工作区；上下文隔离不等于文件隔离或完全盲测，不得创建 Git worktree。

本项目协作流程默认使用以下六个 local 技能，不以原版技能作为默认入口：总管读取 [openspec-explore-local](.agents/skills/openspec-explore-local/SKILL.md)、[openspec-propose-local](.agents/skills/openspec-propose-local/SKILL.md)、[openspec-update-change-local](.agents/skills/openspec-update-change-local/SKILL.md)、[openspec-sync-specs-local](.agents/skills/openspec-sync-specs-local/SKILL.md) 和 [openspec-archive-change-local](.agents/skills/openspec-archive-change-local/SKILL.md)；开发者读取 [openspec-apply-change-local](.agents/skills/openspec-apply-change-local/SKILL.md)。测试角色遵守测试规则，不以技能操作代替独立验收。

正式新增、优化、重构或修复均使用 OpenSpec。新任务先检查工作区；存在未提交改动、未跟踪文件、分支占用或脚本拒绝创建分支时停止并向负责人说明，不自行清理或绕过。满足条件后使用根目录 `start-task.ps1` 从最新 `origin/main` 创建远程任务分支及本地跟踪分支，再运行 `openspec new change <change-id>`。新增、优化、重构分支为 `feature/<name>开发`，修复为 `fix/<name>修复`，name 为简短英文 kebab-case。不得直接在 main、master 开发或修改 OpenSpec；已有 change 先确认所属任务分支，无法确认则停止上报。开发、验证和归档准备均在该分支进行。

`proposal.md` 写背景、范围及验收；`design.md` 写取舍、契约、权限、数据、缓存及风险；`specs/<capability>/spec.md` 写可验证场景；`tasks.md` 写分工、验证证据、问题状态和回滚办法。新增或修改文本的字节转换显式使用 UTF-8；PowerShell 5.1 的非 ASCII 脚本使用带 BOM 的 UTF-8 并验证解析。`ALIYUN_AI_API_KEY` 及相关废弃功能不纳入默认范围，除非负责人明确重启。

共享 `IssueLog.xlsx` 和报告由开发、测试依交接顺序串行写入；独立复核者只读。测试期间开发者停止写入被测文件。需启停本地开发服务时按顺序使用任务分支根目录的 `stop-dev.ps1`、`start-dev.ps1`；默认前端 `127.0.0.1:3000`，后端 `127.0.0.1:8123`，日志在 `tmp/`。`start-main-dev.ps1`、`stop-main-dev.ps1` 仅供负责人查看 main。
