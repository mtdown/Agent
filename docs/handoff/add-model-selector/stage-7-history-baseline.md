# 阶段 7 历史记录补证基准

来源：本次会话的实际工具调用及输出，非事后推测。第一次检查时，`rg --files openspec/changes/add-model-selector docs/handoff/add-model-selector docs/issue-reports/add-model-selector` 输出 `docs/handoff/add-model-selector: 系统找不到指定的文件`；随后本会话依次创建阶段 7 任务卡、派发台账、快照清单和补丁。因此该目录在首次检查时不存在，以下是阶段 8 开始前本会话创建的阶段 7 记录基准。

- `stage-7-snapshot.json`：当时创建输出 SHA-256 `ae028f4abe2fa3d78d704527e7a658210044760f667a686b56bc8d6565cce3dd`。
- `stage-7-snapshot.patch`：当时创建输出 SHA-256 `bb5273c822654c2260ed52514c8d26e6b6012af77194329879011790ffd2110a`。
- `stage-7-tasks.md`：阶段 8 前通过两次 `apply_patch` 写入的完整文本如下（忽略平台换行符）：

```text
# 阶段 7 任务清单

## 7.1 常量与查询
目标：默认 ID 和未知 ID 查询回落为 `cloud-deepseek-flash`，已知 ID 查询不变。允许文件：`cloud_front/src/constants/modelProfiles.ts`。依赖：无。
验收 A1：默认 ID 与未知 ID 查询结果 ID 均为 `cloud-deepseek-flash`；查询 `local-qwen3-1.7b` 仍返回该 ID。verify：阶段 7.3 自动化断言；预期相应断言通过、退出码 0。

## 7.2 状态回落
目标：状态层统一回落默认值。允许文件：`cloud_front/src/stores/useModelProfileStore.ts`。依赖：7.1。
验收 A2：无存储、无效 ID、存储不可用、删除当前自定义档位及选中项失效时返回 `cloud-deepseek-flash`；有效已保存 `local-qwen3-1.7b` 不变。verify：阶段 7.3 自动化断言；预期相应断言通过、退出码 0。

## 7.3 自动化验证
目标：添加覆盖以上行为的可重复自动化断言。允许文件：`cloud_front/tests/model-profile-default.cjs`。依赖：7.1、7.2。
验收 A3：从项目根目录运行 `node cloud_front/tests/model-profile-default.cjs`，覆盖 A1、A2 且退出 0；从 `cloud_front` 运行 `npm run type-check`，退出 0。不修改请求契约。

## 7.4 Vite 类型检查修复
目标：修复 Vite 插件接入引发的类型检查错误。允许文件：`cloud_front/vite.config.ts`、`cloud_front/tsconfig.node.json`。依赖：无。
验收 A4：在 `cloud_front` 运行 `npm run type-check` 和 `npm run build-only` 均退出 0；开发插件仍仅在 dev server 注册，preview 不提供写入端点。verify：以上两条命令及检查插件注册方式，并在独立验收中请求 preview 端点；预期类型检查与构建退出 0，preview 请求不返回插件 JSON。
```

- `dispatch-log.md`：阶段 8 前最后一次更新后的阶段 7 条目如下；后续阶段 8、9 只追加各自条目：

```text
| 7.1 | 调整默认常量及查询回落 | cloud_front/src/constants/modelProfiles.ts | 2026-11-04 | PASS |
| 7.2 | 调整状态层回落 | cloud_front/src/stores/useModelProfileStore.ts | 2026-11-04 | PASS |
| 7.3 | 增加默认行为验证 | cloud_front/tests/model-profile-default.cjs | 2026-11-04 | PASS |
| 7.4 | 修复 Vite 插件类型检查 | cloud_front/vite.config.ts、cloud_front/tsconfig.node.json | 2026-11-04 | BLOCKED |
```

范围限制：上述基准仅覆盖本次会话创建、并能在工具调用中核对的阶段 7 文件，不能证明本会话之前其他位置存在或不存在未记录的交接材料，也不改变阶段 7 的独立测试结论。
