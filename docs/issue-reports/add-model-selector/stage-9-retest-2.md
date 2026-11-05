# 测试报告

Change ID／阶段 ID／原子任务 ID／轮次／时间：add-model-selector／9／9.1–9.4／阶段第1轮、Issue 34/35 修复后复测第2轮／2026-11-04 23:50 +08:00。
测试实例标识：/root/security_retest（独立测试子智能体）。
总体结论：FAIL（A1–A4 固定验收通过；清单外存在交付阻断 Issue 37）。
环境与基线：Windows，Node v24.15.0、npm 11.12.1、Windows PowerShell 5.1.19041.6456；分支 feature/model-selector开发，HEAD f5ecb69bc2ccc292c44fa7ea1ee372abaa34f612。未运行基线；任务卡未提供安全基线运行方法。未启动服务或连接网络，未访问真实 .env.local。
被测范围与快照：六文件及前后 SHA-256 均一致：
- cloud_front/vite/localEnvPlugin.ts：875a9326f2fc7233b4778bab40366f304d558ff595a759736defaaba479b82a4
- cloud_front/src/constants/modelProfiles.ts：801ab07660061aaef5a23ca4a9f462021c0fb1019679223e4e321caab9e3e139
- cloud_front/src/components/ModelSelector.vue：c5ca34b093b4e27dbf336b00ca19a954cdf0b9958d3616249e32361e0eef58ca
- start-dev.ps1：ad0f1f3330db434b89a50ecb395e67975bfe8233b6c45238424215865b8ad1dd
- cloud_front/tests/local-env-isolation.cjs：e867c495e922feba699f46f351ae7c0e19a73b2f286380504d51d5c7fb8f26b7
- scripts/test-start-dev-tunnel.ps1：85868acdc4d0d40134b135048edc8aab9f39517c942d8d581299734b1f19a23d

前后 `Get-FileHash -Algorithm SHA256` 对清单、补丁结果分别为 d52a429a4504d3167364dce0fce9368cbc55399b7c61124e37b12a3340b90792、dcc2004223b15c33968ec6a048a2a8763e7ceaa786a47344e15d4df5709c08a5；分支和 HEAD 均未变化。Python 字节比较交接补丁中 start-dev 的 Git 差异与 `git diff HEAD -- start-dev.ps1` 相同，五个附加新增文件原文与实际文件相同（忽略末尾换行数量）；六文件哈希严格一致。补丁新增文件是原文附录而非 Git 标准 patch，`git apply --reverse --check` 仅验证跟踪文件。测试过程未观察到被测文件并发写入。
开发过程及修改事实记录核验与汇总：任务卡明确未提供独立持久化文件，故无法核对其位置、范围、校验值及开发过程；独立检查了上述实际代码、快照及测试行为，不以开发自检替代验收。

验收项结果：
- A1：PASS。根目录 `node cloud_front/tests/local-env-isolation.cjs` 退出 0；只读审阅断言：内存文件模拟零、一、重复同名键，逐字比较新值和其他键、注释及顺序，并检查临时写入及重命名。无真实密钥。
- A2：PASS。相同命令退出 0；请求构造为 POST、空 headers，验证允许键占位值写入、非法键、空白值和换行值返回 400 且不写入；标志 1 时 handler 未注册。测试将真实组件的两个函数提取到 VM，模拟 404，断言 envWritable 为 false，另检查模板提示和 disabled；代码核对确认调用与禁用绑定。前端 `npm run type-check`、`npm run build-only` 均退出 0；遍历 dist 全部文件字节查找 `/__cw/local-env` 命中文件数 0。构建提示部分 chunk 大于 500 kB。
- A3：PASS。根目录 `powershell -NoProfile -File scripts/test-start-dev-tunnel.ps1` 退出 0，在 PowerShell 5.1 中 AST 解析通过；测试隔离提取条件并 stub Start-Process，断言 default、NoTunnel、Tunnel、both 传递标志 0、0、1、0，且分别检查两种前端启动分支。输出对应隧道调用次数 0、0、1、0（脚本输出顺序 default、Tunnel、NoTunnel、both）。未启动服务。
- A4：PASS。上述两个测试、type-check、build-only 均退出 0，生产资产端点命中 0；无真实服务或密钥访问。

固定验收缺陷：无。
清单外交付阻断问题：Issue 37。内存中 esbuild 打包插件，将 node:fs 替换为不会访问磁盘的 stub，截获注册 handler；向 handler 传递 Readable.from(['null'])，method POST，以 unhandledRejection 监听结果：`TypeError Cannot read properties of null (reading 'key')`，`response sent: false`。实际位置 cloud_front/vite/localEnvPlugin.ts 的 JSON.parse 后 payload.key 访问；影响为异常输入导致未处理拒绝，运行策略可能导致进程退出或请求挂起。此独立子进程未启动网络服务或接触真实文件。
未验证项与原因：真实网络和真实密钥文件按任务卡禁止；未运行基线。未提供开发过程持久化记录。
变异检验：未做，任务卡未授权。
问题记录：Issue 34 复测通过，零、一、多同名键行为满足新验收；Issue 35 按新要求复测通过，新需求明确替代旧的来源验证要求，现实现不再检查 Origin、自定义头或来源，不能声称旧来源验证存在。原始证据保留在 docs/issue-reports/add-model-selector/stage-7-static-review.md 和 stage-7-static-review.html；Issue 37 待处理，证据为本报告。
临时文件、数据及服务清理状态：未创建临时目录；仅产生标准构建产物和 TypeScript 缓存，未启动服务。未删除任何临时目录。
风格意见（不计 FAIL）：无。
