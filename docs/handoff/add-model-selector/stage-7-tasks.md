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
