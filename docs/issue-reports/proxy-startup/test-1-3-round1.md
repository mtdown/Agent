# 测试报告

Change ID／阶段 ID／原子任务 ID／轮次／时间：proxy-startup／1／1.3／第 1 轮／2026-11-01（Asia/Shanghai）  
测试实例标识：/root/proxy_tester_1_3  
总体结论：PASS

环境与基线：Windows 本机 Docker，分支 fix/proxy-startup修复，HEAD 5b1c31926409537d2e8843f65c908fa4fdc1b27e；未切换或运行基线，未运行部署或 CI。测试数据为随机标识的容器、网络及新建临时目录内的两份后端配置；请求在代理容器内部使用 loopback，未连接生产服务。

被测范围与快照：测试前后 Get-FileHash -Algorithm SHA256 核对 manifest 的 11 个文件均匹配；manifest SHA256 3307d7ed5ba2540eed234bedefcefa6b6bc7f6bb7da5913392d12992a2cae050，完整差异 5893b9f34c68e03b99e2f9312d8ceb1a3796bb8dc9fbfeaddb9480e783a5f278。测试前 Git 状态包括修改的 AGENTS.md、AGENTS_manager.md、AGENTS_test.md、deployProxy.test.mjs；未跟踪 deployProxyLifecycle.test.mjs、proxyLifecycle.mjs、openspec/changes/proxy-startup 的六个文件及八个既有开发和测试报告。测试后被测文件状态未变，另新增本报告。完整差异的 tracked 部分与 git diff -- 一致，七个未跟踪源码及 OpenSpec 内容分别与快照一致（比较时仅忽略末尾换行）。

开发事实记录核验：docs/issue-reports/proxy-startup/developer-1-3-round1.md 测试前后 SHA256 为 f5752459854504bf09027fa5df306a9bdae8e2e1d23d67f17d9842f5acdbb354。其描述的目录保留、平台转义和无 workflow 改动通过本轮独立源码审查和测试验证；开发者先前两次运行和其临时目录未重放或读取，开发者自检不作为独立测试结果。

验收项结果：

- A1：PASS。node --test deployProxyLifecycle.test.mjs 退出 0，23 项通过；受控替身验证延迟时先 HTTP 探测再 cp、nginx -t、reload，退出和超时抛错且不复制配置。
- A2：PASS。同一命令通过启动失败、非法配置、reload 失败、诊断失败、状态和日志清理前输出、原始异常保真、受控敏感字符串不输出及未确认归属资源不诊断等断言。
- A3：PASS。同一命令验证操作顺序、资源 ID 及归属；只读审查确认仅测试注册的容器和网络清理，目录不删除。git diff --check 退出 0，完整差异未改变生产配置、部署 workflow 或依赖。
- A4：PASS。node deployProxy.test.mjs 退出 0，输出 Backend replacement proxy test: passed (backend 172.21.0.2 -> 172.21.0.4, proxy never restarted)；源码断言交付配置 nginx -t 和 reload、A/B 两次 /api/ping 原样透传、IP 不同及代理 ID、启动时间和重启计数不变。
- A5：PASS。只读审查三份测试源码无目录删除调用；受控测试断言 Windows 和 POSIX 单引号路径转义、成功和失败后报告及未创建时不报告。真实测试输出下列精确路径和 PowerShell 命令，Test-Path -LiteralPath ... -PathType Container 为 True，目录含两份后端配置；按本轮 owner 标签的 docker ps -a 与 docker network ls 均无结果。git diff -- .github/workflows/deploy.yml 为空。

固定验收缺陷：无。清单外交付阻断问题：无。未验证项：未执行真实 CI 或部署，无法证明历史 CI 故障根因。变异检验：未获任务卡授权，未做。问题记录：无新增或更新。风格意见：无。

临时目录、数据及服务状态：本测试产生下列一个目录，存放测试专用 backend-a.conf 和 backend-b.conf，仍存在，待管理员验收后处理；测试者未执行以下命令：

```powershell
Remove-Item -LiteralPath 'C:\Users\origin\AppData\Local\Temp\cw-proxy-test-77940-nb1ivk' -Recurse -Force
```

本轮测试专有容器和网络按标签查询均为空；未启停开发服务。测试未删除任何临时目录，未访问用户数据或过去被策略拒绝的目录。
