# 派发台账

| 任务 ID | 一句话目标 | 涉及文件 | 派发时间 | 状态 |
| --- | --- | --- | --- | --- |
| 7.1 | 调整默认常量及查询回落 | cloud_front/src/constants/modelProfiles.ts | 2026-11-04 | PASS |
| 7.2 | 调整状态层回落 | cloud_front/src/stores/useModelProfileStore.ts | 2026-11-04 | PASS |
| 7.3 | 增加默认行为验证 | cloud_front/tests/model-profile-default.cjs | 2026-11-04 | PASS |
| 7.4 | 修复 Vite 插件类型检查 | cloud_front/vite.config.ts、cloud_front/tsconfig.node.json | 2026-11-04 | BLOCKED |
| 8.1 | 关闭默认临时隧道并支持显式启用 | start-dev.ps1 | 2026-11-04 | PASS |
| 8.2 | 覆盖隧道启动参数组合 | scripts/test-start-dev-tunnel.ps1 | 2026-11-04 | PASS |
| 8.3 | 同步当前启动使用文档 | README.md、docs/demo-public-tunnel.md | 2026-11-04 | PASS |
| 9.1 | 覆盖同名密钥并去重 | cloud_front/vite/localEnvPlugin.ts | 2026-11-04 | PASS |
| 9.2 | 简化端点并支持隧道禁用 | cloud_front/vite/localEnvPlugin.ts、cloud_front/src/constants/modelProfiles.ts、cloud_front/src/components/ModelSelector.vue | 2026-11-04 | PASS |
| 9.3 | 向前端传递隧道禁用信号 | start-dev.ps1 | 2026-11-04 | PASS |
| 9.4 | 隔离验证覆盖与禁用行为 | cloud_front/tests/local-env-isolation.cjs、scripts/test-start-dev-tunnel.ps1 | 2026-11-04 | PASS |
