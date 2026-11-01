# 测试报告

Change ID／阶段／任务／轮次／时间：proxy-startup／1／1.1／第 3 轮／2026-11-01 16:54–16:57 +08:00。测试实例：`/root/proxy_tester_r3`。总体：BLOCKED（固定 A1–A4 均通过，但本轮测试临时目录清理命令被自动策略拒绝）。

环境与基线：Windows，Docker Server 29.2.0；分支 `fix/proxy-startup修复`，HEAD `5b1c31926409537d2e8843f65c908fa4fdc1b27e`。未切换基线或运行基线；未运行部署或 CI。原 CI run 当次根因未知，本机结果不能证明其根因。中断后检查未发现 `deployProxy.test.mjs` 进程；中断前真实回归尚未启动。

被测范围与快照：完整差异 `tmp/proxy-startup-r3-review/complete-diff.txt` SHA256 `654c65dd0b9b95cf67e0ab8826454d4463a95b4bc294e22893abc52e980a83c1`；清单 `tmp/proxy-startup-r3-review/manifest.json` SHA256 `cd3cde2e89ba7323f7021541a1bf88870fd23211e99cf9d4725a3ba50c393339`。测试前后对清单 8 个文件逐一 `Get-FileHash -Algorithm SHA256`，全部匹配：deployProxy.test.mjs、deployProxyLifecycle.test.mjs、proxyLifecycle.mjs、OpenSpec 的 .openspec.yaml、design.md、proposal.md、specs/deployment-pipeline/spec.md 和 tasks.md；清单和差异文件哈希不变，分支及 HEAD 不变。`git status --short` 与冻结范围一致，报告目录为既有未跟踪目录。完整 diff 已审阅。

开发记录：`docs/issue-reports/proxy-startup/developer-1-1-round3.md` SHA256 `2fc950461bb155c6e89455ca6513593088e685ba9333058c0d10dd36a859c321`，前后匹配；其安全分类、重启快照及本机测试的主要事实由下列独立测试验证，开发的红灯及其具体运行过程未独立重演。

验收项：

- A1 PASS：`node --test deployProxyLifecycle.test.mjs` 退出 0，20 项通过。延迟双例断言 wget 就绪后才 cp、nginx -t 和 reload；退出、超时均拒绝并断言未 cp。
- A2 PASS：上述受控测试覆盖退出、超时、非法配置、reload、诊断失败，断言诊断先于清理、原异常保留、状态与退出码及安全日志类别；归属检查避免诊断陌生容器。额外调用 `withProxyDiagnostics` 令诊断命令抛错，确认保留同一原始错误且执行清理。独立 CLI 子进程在本轮目录编译 fake docker 并通过 PATH 注入：create、exit、nginx-check、reload、timeout 均退出 1，分别匹配 `create; exit code 7`、`proxy-exited`、`nginx-check; exit code 42`、`nginx-reload; exit code 43`、`proxy-timeout`，随机无害哨兵未出现。fake docker 未连接真实 daemon。
- A3 PASS：同一 20 项测试断言调用次序，container/network 创建失败时 owned ID 清理、foreign/missing/unknown 不删除；`git diff --check` 退出 0，完整差异审查未见生产配置、CI workflow、依赖、外部接口变更。
- A4 PASS：`node deployProxy.test.mjs` 退出 0，真实 Docker 隔离网络中输出 `Backend replacement proxy test: passed (backend 172.21.0.2 -> 172.21.0.4, proxy never restarted)`。脚本实际断言配置检查及 reload、前后 A/B 的 `/api/ping` 原样路径、IP 不同和代理快照。独立受控反例通过 `proxySnapshot` 对同一 ID 返回 StartedAt first/second 和 RestartCount 0/1，`assertSameProxy` 抛错；分别只更改 ID、StartedAt 或 RestartCount 也均抛错；确认两次 inspect 都针对同一 ID。该反例为模拟，不声称实际重启了真实容器。

固定验收缺陷：无。清单外交付阻断问题：无已证实产品回归。未验证：真实 CI 当次原因和基线比较；CLI 请求类别由既有受控测试覆盖，未对子进程注入请求故障。未做变异检验（任务卡未授权）。问题 #18 与 #19 的对应复测通过，分别关闭，证据为本报告。

清理：真实回归进程 PID 11632，观察到 `cw-proxy-test-11632-t1ow0p` 的 a、b、filler、proxy 四容器；结束后按 PID 唯一前缀查询容器、网络及系统临时目录，均为空。测试产生的本轮目录 `tmp/proxy-startup-r3-review/tester-810a0eea40d242e38702efcea45d2347` 含 fake Docker 源码、可执行文件与真实测试输出；针对该目录的清理命令被自动策略拒绝，未绕过，目录保留，需负责人处理。未触碰此前被策略拒绝的目录或其他资源。

风格意见：无。
