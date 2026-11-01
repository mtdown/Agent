# 测试报告

Change ID／阶段 ID／原子任务 ID／轮次／时间：proxy-startup／1／1.1／1／2026-11-01 16:31 Asia/Shanghai
测试实例标识：/root/proxy_tester_r1
总体结论：FAIL；A2 另有证据不足。
环境与基线：Windows，Node v24.15.0，Docker server 29.2.0，本机现有 nginx:1.27-alpine 镜像；分支 fix/proxy-startup修复，HEAD 5b1c31926409537d2e8843f65c908fa4fdc1b27e。未运行基线，任务卡未授权切换或覆盖；未重跑 CI 或部署。CI 当次根因未知。
被测范围与快照：测试前后 git 状态一致，均为修改 deployProxy.test.mjs，新增 deployProxyLifecycle.test.mjs、proxyLifecycle.mjs、开发记录及 openspec/changes/proxy-startup 下五个文件。逐项 Get-FileHash SHA256 核对 manifest 所列全部八个文件前后均 OK；manifest b40f0d5a949e46ccc91f0b611ceb8959be7908bb25c3c05ca20fc90d4360f190，完整差异 cbc17515937a375fe3a8e001c6a01103732b57d5597b4d58f02b91e8c932eaec；开发记录 1bb4622f1c62ef4be7945a02975dcca7ddd503a108249c655f5069ffab110465；前后匹配任务卡。测试报告为授权新增文件，写入在上述测试后核验之后。
开发过程及修改事实记录核验与汇总：读取 docs/issue-reports/proxy-startup/developer-1-1-round1.md 和完整差异。文件范围、保留的真实测试断言及本轮运行结果与记录相符；原始 TDD 红灯和修复前六次结果仅为开发记录，未独立重现；“只对成功创建的容器执行清理”所述机制经检查存在 #16 的失败后遗留风险。真实测试运行通过不能确认 CI 当次原因。
验收项结果：
- A1：PASS。node --test deployProxyLifecycle.test.mjs 运行 1 次，退出 0，7 项通过。受控替身延迟两次后就绪，断言探测、复制、nginx -t、reload 顺序；退出及超时均拒绝且不执行 cp。
- A2：证据不足。相同命令中退出、超时、nginx -t 失败、reload 失败及诊断失败保留原异常，断言读取状态/退出码、日志后清理；但未模拟 docker run 非零且代理已创建的路径，亦未断言敏感日志或无关容器信息，见 #17。无法以 7 项通过证明完整固定验收。
- A3：FAIL，#16。相同命令的清理单元测试断言只删除传入资源，git diff --check 退出 0，完整差异未修改生产配置、workflow、依赖或接口。但实际脚本仅在 backend A、filler、backend B 的 docker run 成功后登记容器。独立一次性实验 `docker run -d --name <随机名> --entrypoint /nonexistent-test-command nginx:1.27-alpine` 返回 127，随后 inspect 返回 0、状态 created，说明非零不代表未创建；该路径不会登记容器，网络移除亦可能失败且被忽略。该实验验证 Docker 行为并结合代码判断，未注入业务脚本端到端复现。
- A4：PASS。node deployProxy.test.mjs 运行 1 次，退出 0，输出 `Backend replacement proxy test: passed (backend 172.21.0.2 -> 172.21.0.4, proxy never restarted)`；代码保留配置检查、不同 IP、代理状态和 A/B 的 /api/ping 原样断言。测试使用随机网络和本地 Nginx 容器，不连接生产服务。
固定验收缺陷：#16，A3，见以上复现及代码证据。#17 为 A2 证据不足。
清单外交付阻断问题：无另项。
未验证项与原因：未独立重现开发红灯和修改前六次；未验证原 CI 失败原因。A2 缺少上述路径与输出断言。
变异检验：未做，任务卡未授权。
问题记录：#15 更新待处理；#16 待处理；#17 证据不足。证据为本报告和开发记录。
临时文件、数据及服务清理状态：真实测试使用随机资源并自行清理；事后查询 cw-proxy-test- 前缀无容器或网络。独立 Docker 实验产生的两个随机 cw-proxy-validation- 容器均由本实例删除，删除后 inspect 非零；无测试脚本文件生成。未接触已有服务和此前禁止清理目录。
风格意见：无。
