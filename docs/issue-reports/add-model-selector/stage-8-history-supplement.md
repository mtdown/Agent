# 测试报告

Change ID／阶段／任务／轮次／时间：add-model-selector／8／8.3／第 1 轮补证／2026-11-05 10:25 +08:00  
测试实例：/root/history_test（独立实例，共享工作区）  
总体结论：证据不足。现存内容与重建基准匹配，但本实例无法核对基准所称的历史原始工具调用与输出，无法独立确认任务卡和台账的历史真实性及历史清单完整性。

环境与基线：Windows PowerShell；分支 feature/model-selector开发，HEAD f5ecb69bc2ccc292c44fa7ea1ee372abaa34f612；未运行基线。无网络、服务、密钥访问。

被测快照：测试前后使用 `Get-FileHash -Algorithm SHA256`，七项均与任务卡一致：stage-7-history-baseline.md 5cc75b42906942ef844b4fcbe95c0638f10c31d8cd477f268a771e91a1ad6b2e；stage-7-tasks.md 99cb53f687068705eccc9a04c1c7096712ec622f9a5218ba5d65c2e0a80a0708；dispatch-log.md d7b0b72dad322ddd3c07e4479241cf604a5a97175b68d5d71da1f6d9d50eabb6；stage-7-snapshot.json ae028f4abe2fa3d78d704527e7a658210044760f667a686b56bc8d6565cce3dd；stage-7-snapshot.patch bb5273c822654c2260ed52514c8d26e6b6012af77194329879011790ffd2110a；README.md 420ca6a6481aaee9203ad97f931b7b096ea9c4ecdd3d46f8e7e5657ff66d554a；docs/demo-public-tunnel.md f85f2d222c50f26b85c3f520d6a2a51d3910131c5ef391f05710d42da0bde6df。前五项路径均在 docs/handoff/add-model-selector/ 下。测试前后分支及 HEAD 一致。

开发过程及修改事实记录：读取 stage-7-history-baseline.md；其声明由本会话工具输出重建，记录首次目录检查、两次写入任务卡、四项台账及两项快照哈希，且限定不能推断会话之前其他位置的记录。本实例不继承总管会话，无法读取和核实基准引用的原始工具调用及输出；重建基准本身及其与当前文件的相等不能独立证明历史原貌。原始 stage-7-round-1.md 独立记载快照清单和补丁的两项摘要，但未记载任务卡及台账全文或摘要。

验收项：
- 历史任务卡及台账：证据不足。Python 以 UTF-8 读入，按通用换行规范化后提取基准首个 text 代码块并和任务卡全文相等比较，结果 True（各 1018 字符）；第二代码块四行分别在当前台账恰好出现一次。现存文件和重建基准匹配，但未取得可独立复核的历史原始输出。
- 阶段 7 清单和补丁：PASS。Python hashlib SHA-256 对两文件计算，分别为 ae028f4abe2fa3d78d704527e7a658210044760f667a686b56bc8d6565cce3dd 和 bb5273c822654c2260ed52514c8d26e6b6012af77194329879011790ffd2110a；两个值均见重建基准和 stage-7-round-1.md。
- A3 文档与脚本：PASS（静态对照）。`rg -n -- '-Tunnel|-NoTunnel|默认' README.md docs/demo-public-tunnel.md` 和 `rg -n -- 'Tunnel|NoTunnel' start-dev.ps1` 退出 0；README 第 280 行与演示文档第 8–15、46–47 行说明默认关闭、显式开启和关闭优先，当前脚本第 246、401–403 行以 `$Tunnel -and -not $NoTunnel` 控制。未启动服务。当前 start-dev.ps1 摘要 ad0f1f3330db434b89a50ecb395e67975bfe8233b6c45238424215865b8ad1dd，不同于阶段 8 初测快照 95cd63cb6b8d3bde2789ec2028ac16e284eab7ad592d51c610ccd923b66647f1；因此此静态检查仅说明当前脚本，初测结果仍以原报告为准。

固定验收缺陷：无已确认功能缺陷；历史真实性和完整性仍为 Issue 36 证据不足。清单外交付阻断问题：无独立确认。未验证项：基准所引原始会话输出，以及会话之前其他位置是否有历史资料。未做变异检验，任务卡未授权。临时文件、数据及服务：未创建或删除临时目录、数据或服务。风格意见：无。

## 原始会话记录补核（2026-11-05）

本节追加证据并替代上文“总体结论”和“历史任务卡及台账”中的证据不足结论；原报告保留以反映当时结果。补核结论：PASS，范围为阶段 8 开始前本会话创建的阶段 7 交接记录。未推断本会话之前其他位置有无资料，也不改变阶段 7 原有 BLOCKED 验收结论。

原始记录定位：Codex chat `01a1a6df-02a8-74c2-b599-8bbae2ee2da1`，hostId `local`，使用 `mcp__codex_app__read_thread`，`includeOutputs=true`；以下 ID 为原始会话中可定位的 item ID：

- 首次检查目录：turn `01a1a6df-bd6d-7363-89bd-f988297c647e`，`exec-6e96b95a-341a-48b5-88d5-c241fd9f9d54`，`rg --files` 返回交接目录不存在（退出 1）。该证据只说明该路径在检查时不存在。
- 创建和追加任务卡：turn `01a1a6e9-08c7-75d3-8225-f3ddde0e1659`，文件变更 `exec-8a3742f8-9826-4b59-8c05-3f476c261d78` 包含创建任务卡的 7.1–7.3 全文及初始空台账，`exec-eecfc879-6d78-404f-8d7c-785d209b6ffe` 包含追加 7.4 的完整差异；二者未截断。原始读取 `exec-7825e758-40e6-4548-9f23-d0e09da31ae1` 的完整输出与当前任务卡使用同一 `Get-Content -Raw -Encoding UTF8` 读取后的输出，在统一换行后逐字相等（均 1019 字符，含命令输出尾部换行）。
- 台账：同一 turn 的 `exec-22e00853-9754-492b-a9a4-e2dd4fe625da`、`exec-cd862d86-30c9-46ba-b54d-271ddb0a7238`、`exec-51728d03-a71e-4e0a-8c0f-bca2b8e5a856`、`exec-eecfc879-6d78-404f-8d7c-785d209b6ffe` 记录派发与更新；`exec-6a203a1b-c327-4aac-8c40-a0330a295ffc` 记录四行最终 PASS、PASS、PASS、BLOCKED，四行各与当前台账恰好匹配一次。阶段 8 增加台账行的原始变更 `exec-ebe7c481-9e53-4f9a-94ed-28e81cc75fe1` 保留了 7.4 行作为上下文。
- 快照：同一 turn 的 `exec-ae966de0-3a77-40c5-aca8-74c24c41dc2e` 原始创建输出记录 22 文件、清单 SHA-256 `ae028f4abe2fa3d78d704527e7a658210044760f667a686b56bc8d6565cce3dd` 和补丁 SHA-256 `bb5273c822654c2260ed52514c8d26e6b6012af77194329879011790ffd2110a`；两值与前次本报告的实测及阶段 7 原始测试报告一致。

补核只读取原始会话及现存文件，没有重跑测试、修改被测文件或访问服务。结合本报告前次完成的七文件前后快照校验、文档静态检查和上述原始记录，阶段 8.3 历史记录补证通过。Issue 36 关闭；原阶段 8 第 1 轮报告的当时证据不足结论保留。
