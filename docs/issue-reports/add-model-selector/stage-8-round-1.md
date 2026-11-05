# 测试报告

Change ID／阶段 ID／原子任务 ID／轮次／时间：add-model-selector／8／8.1–8.3／1／2026-11-04 23:02 +08:00
测试实例标识：/root/tunnel_tester（独立测试子智能体，共享工作区）
总体结论：证据不足（A3 历史记录保留情况缺少对照基准；A1、A2 通过）
环境与基线：Windows PowerShell 5.1.19041.6456；分支 feature/model-selector开发；HEAD f5ecb69bc2ccc292c44fa7ea1ee372abaa34f612。仅静态检查及桩测试，未运行基线、真实服务或网络；未检查或改变现有 Cloudflared 服务状态。

被测范围与快照：完整阶段文件为 README.md、docs/demo-public-tunnel.md、scripts/test-start-dev-tunnel.ps1（新增）、start-dev.ps1。前后均用 `Get-FileHash -Algorithm SHA256` 检查，分别为 420ca6a6481aaee9203ad97f931b7b096ea9c4ecdd3d46f8e7e5657ff66d554a、f85f2d222c50f26b85c3f520d6a2a51d3910131c5ef391f05710d42da0bde6df、889328410d77b047c51c361db49deccb933aca2cc5cba17739608731b3bbc97a、95cd63cb6b8d3bde2789ec2028ac16e284eab7ad592d51c610ccd923b66647f1；清单 d4556efa1c9bbe8a55b07f891e5bf9327024f8f2e21da021d87be1505a9df101；补丁 e08c37018ace211bd895de563586b8b95c187ce42e4d9a7a494e48b7c130e68f。前后均一致，分支及 HEAD 一致。git diff/name-status 与未跟踪文件检查确认三个已修改及一个新增；以 Git 差异对比补丁的已跟踪部分、以实际文件内容对比补丁新增部分，均一致（统一换行后比较）。

开发过程及修改事实记录核验与汇总：任务卡明确未提供独立持久化记录，无法核对过程、位置、范围及摘要；仅能独立核对上述快照与实际差异，不推断开发过程。

验收项：
- A1：PASS。执行 `powershell -NoProfile -File scripts/test-start-dev-tunnel.ps1`；退出 0，默认、Tunnel、NoTunnel、both 的调用次数依次为 0、1、0、0，符合预期。`powershell -NoProfile` 下 Parser.ParseFile 对 start-dev.ps1 报错数 0。代码检查确认实际调用由 `$Tunnel -and -not $NoTunnel` 控制。
- A2：PASS。相同命令在仓库根目录执行，输出四项计数与 `PowerShell AST parse and tunnel switch checks passed.`；退出 0。审阅测试脚本：ParseFile 读取真实脚本，提取参数 AST、调用所在 if 条件及调用文本，执行提取出的参数和条件，调用由本地同名计数桩替代；并未调用真实启动脚本或服务。额外检查两个脚本均以 Windows PowerShell 5.1 解析，错误数 0；start-dev.ps1 UTF-8 BOM 字节为 EF BB BF，测试脚本无 BOM 但仅含 ASCII。
- A3：证据不足。执行 `rg -n -- '-Tunnel|-NoTunnel|默认' README.md docs/demo-public-tunnel.md`，退出 0；README 第 280 行及公网演示文档第 8–15、46–47 行分别说明默认关闭、显式开启、NoTunnel 优先，与脚本一致。历史交接目录中可见 dispatch-log.md 和阶段 7 的清单、补丁、任务文件，但该目录未跟踪，且任务卡没有以前的完整清单或摘要，不能独立确认历史内容完整保留。

固定验收缺陷：无已确认行为缺陷；A3 历史交接记录的证据不足登记为 issue 36，状态“证据不足”。
清单外交付阻断问题：无发现。
未验证项与原因：历史交接记录完整保留无法比较，缺少此前基准；开发过程及修改事实的独立记录未提供。没有启动真实服务、隧道或网络，符合任务卡限制。
变异检验：未做，任务卡未授权。
问题记录：issue 36；本报告为证据。
临时文件、数据及服务清理状态：未创建临时目录、测试数据或服务；未删除目录，现有服务状态未改变。
风格意见：无。
