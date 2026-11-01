# 测试报告

Change ID／阶段 ID／原子任务 ID／轮次／时间：proxy-startup／1／1.1／2／2026-11-01（Asia/Shanghai）
测试实例标识：/root/proxy_tester_r2；总体结论：PASS。

环境与基线：Windows，本机 Docker daemon 29.2.0，nginx:1.27-alpine 镜像已存在；分支 fix/proxy-startup修复，HEAD 5b1c31926409537d2e8843f65c908fa4fdc1b27e。未切换运行基线，无法判定原 CI 失败原因；本机通过不证明 CI 根因。

被测快照：manifest.json SHA256 9aed1180826df277e1d8ae373c6b5555db723d8c65f5cec44fffec66c6434f29；完整差异快照 SHA256 922d544ee9ad8bd8af34ad9101a32ad70744eb991afb99f422166ea37cfc7272。前后用 Get-FileHash -Algorithm SHA256 逐项核对清单全部一致：deployProxy.test.mjs、deployProxyLifecycle.test.mjs、proxyLifecycle.mjs，以及 openspec/changes/proxy-startup/ 下 .openspec.yaml、design.md、proposal.md、tasks.md、specs/deployment-pipeline/spec.md；HEAD 和分支未变。完整差异含未跟踪文件，已阅读范围。

开发过程及修改事实：developer-1-1-round2.md SHA256 c0cec5730ccd61763231d45af35c230089d02cbb30499bb196007332602c5482，前后相符。独立运行 18 项测试及真实回归，并检查代码与补充完整脚本子进程故障注入；支持其当前功能和限制的描述，历史红灯及之前运行未独立重放。CI 原因未知。

验收项：

- A1：PASS。`node --test deployProxyLifecycle.test.mjs` 退出 0，18/18 通过；测试断言两次未就绪后再复制，退出和超时不复制并抛出错误。
- A2：PASS。相同命令断言启动退出、超时、配置检查、reload 和诊断失败时原异常保留，清理前检查状态及日志；归属不可确认时不诊断。另用本轮目录下编译的 fake docker.exe 对完整 `node deployProxy.test.mjs` 注入 nginx -t 非零 42 和代理 run 非零 7（创建后可按标签识别）两种情况：脚本均退出 1；生成的随机无关键字模拟秘密均未出现在输出；配置检查显示 `nginx-check`、`unknown-directive`、`/etc/nginx/conf.d/default.conf`、行 19，状态 `exited 17`；创建失败先按本轮标签核验，再只对 proxy-id 做状态/日志查询，清理 proxy-id、backend-id、network-id。两场景诊断先于清理，输出不含哨兵。此样例不推断任意字符串均可识别为秘密。
- A3：PASS。受控测试覆盖容器与网络创建非零后的已归属、缺失、其他归属和检查失败，断言仅按核实 ID 清理且保留原异常；就绪、复制、检查和 reload 顺序通过。`git diff --check` 退出 0；范围审查未发现生产配置、CI workflow、外部接口或依赖变更。
- A4：PASS。`node deployProxy.test.mjs` 退出 0，输出 `Backend replacement proxy test: passed (backend 172.21.0.2 -> 172.21.0.4, proxy never restarted)`；代码断言配置检查、后端地址不同、代理未重启及 `B:/api/ping` 原样传递。运行的是随机标签专用 Nginx 网络和容器，未连接生产服务。

补证 #16：本轮随机标签独立真实容器以不存在的 entrypoint 创建，docker run 退出 127，而 inspect 显示 created、退出码 127；核对标签后按不可变 ID 3d952b866457de787f22d76716bfb6cf1b9b58ad337dd045db2dabecfaeb0eac 删除，退出 0，标签查询无残留。此实验验证实际 Docker 创建失败可遗留容器；不同归属、检查失败和网络创建失败由受控测试验证。

固定验收缺陷：无。清单外交付阻断问题：无。未验证项：原 CI 根因和其他环境行为未验证，不属于本机通过的推断。变异检验：未执行，任务卡未授权修改被测文件。问题记录：#15、#16、#17 依本轮证据更新。风格意见：无。

清理：测试专用 fake docker 目录 tmp/proxy-startup-r2/cli-inject 仍保留（含模拟程序和调用轨迹）；自动审批策略拒绝了清理命令，未绕过该拒绝。真实实验只删除核实归属的本轮 ID。真实测试前后按 proxy-regression-owner 标签查询容器和网络均为空；未操作现有服务或其他资源。
