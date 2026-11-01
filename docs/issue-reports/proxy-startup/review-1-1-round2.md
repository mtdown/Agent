# 只读复核记录

Change proxy-startup／任务 1.1／第 2 轮；复核实例 `/root/proxy_review`；2026-11-01。输入：冻结清单 SHA-256 `9aed1180826df277e1d8ae373c6b5555db723d8c65f5cec44fffec66c6434f29`、完整差异 SHA-256 `922d544ee9ad8bd8af34ad9101a32ad70744eb991afb99f422166ea37cfc7272`、固定 A1–A4 和 `test-1-1-round2.md`。复核只读，不重跑测试、不改文件或数据。

1. `proxyLifecycle.mjs` 的 `reportFailure` 仅输出固定 operation，启动提前退出与 readiness 超时都回落 `regression`，Docker 创建失败的命令退出码也不再显示。内部测试验证抛出相同异常，但未证明 CLI 保留可用的失败分类。应保持自由文本脱敏，同时保留安全的失败阶段和数值退出码。
2. `deployProxy.test.mjs` 检查 `.State.Restarting` 的瞬时值不能证明容器整个后端替换期间未重启；这是基线已有检查，不能单凭它声称完整时段未重启。需要对同一容器使用前后可验证状态作对比。

复核结论：需补充上述实现和验证；CI 原容器退出原因仍未知。
