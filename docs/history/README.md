# docs/history — 历史台账归档

> 本目录存放**已停止维护、但需留档追溯**的台账与图表。
> 现役台账是仓库根目录的 `IssueLog.xlsx`（`AGENTS.md` §3 指定的问题记录载体）。

## 归档内容

| 文件 | 原位置 | 停更时间 | 说明 |
|---|---|---|---|
| `变更台账.xlsx` | 仓库根目录 | 2026-09-06 | 早期「目的 / 分支 / openspec」三列变更记录，9 条。记录的是云图库 → Wiki 空间 → Wiki-First 阶段的变更 |
| `项目问题记录表.xlsx` | 仓库根目录 | 2025-10-26 | 更早的问题记录，内容为 Maven 仓库解析、跨域、`basicLayout` 样式等，属**另一个前端项目**。Sheet2 / Sheet3 为空 |
| `UML后端.svg` | 仓库根目录 | 2025-09-25 | 后端结构图，近一年未更新 |

**为什么归档而不是删除**：三份文件被 git 跟踪，历史可追溯（迁移时全部识别为 `R100` 重命名）。
其中 `变更台账.xlsx` 保留了「哪次变更走了哪条分支、对应哪个 OpenSpec change」的对应关系，
这类信息在 `git log` 里虽有但需要自行重建，留档成本为零。

---

## ⚠️ 关于台账未被同步的一处"疑似待办"

`变更台账.xlsx` 最后一行写着：

| 列 | 内容 |
|---|---|
| 目的 | 前端存量类型清理：52 个 TS 错误致 `npm run build` 红（图库/管理页历史债务；`requestType` 重生成回归也应在此剔除） |
| 分支 | 待切分支（建议 `fix/frontend-typecheck修复`） |
| openspec | 待立项 |

**经核实：这件事已经完成，不是待办。** 对应的 change 已建、已执行、已归档：

- 路径：`openspec/changes/archive/2026-09-06-fix-frontend-typecheck/`
- 其 `tasks.md` 中「0. Baseline And Scope」「1. API Response Type Contract」等各节任务**全部已勾选 `[x]`**
- 卡片本身明确写着 "this change fixes only the 52 legacy frontend TypeScript errors"

**即：台账停更于 2026-09-06，而该 change 恰在 2026-09-06 归档 —— 台账没来得及同步最后一条的状态。**
把它当作"未闭环待办"会造成误判，特此更正。
