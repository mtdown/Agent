# 测试报告

Change ID／阶段／任务／轮次：add-model-selector／阶段 7／7.1–7.4／第 1 轮
时间：2026-11-04 22:31–22:33 Asia/Shanghai；测试实例：/root/tester（独立子智能体）。
总体结论：BLOCKED（A4 的 preview HTTP 验收未执行；其余结果如下）。

环境与基线：Windows，PowerShell，本机 Node/npm；分支 feature/model-selector开发，HEAD f5ecb69bc2ccc292c44fa7ea1ee372abaa34f612。未切换分支或运行基线，故未比较基线运行结果。任务卡声明无独立持久化开发过程及修改事实记录，无法核对该记录；不据此推断开发过程。

被测快照：完整 22 个文件及其 SHA-256 见 docs/handoff/add-model-selector/stage-7-snapshot.json，摘要 ae028f4abe2fa3d78d704527e7a658210044760f667a686b56bc8d6565cce3dd；完整补丁 docs/handoff/add-model-selector/stage-7-snapshot.patch，摘要 bb5273c822654c2260ed52514c8d26e6b6012af77194329879011790ffd2110a。测试前后分别以 PowerShell Get-FileHash -Algorithm SHA256 核对清单全部文件、清单和补丁；以 git diff --name-only HEAD 与 git ls-files --others --exclude-standard 合并排序，比对清单，排除 handoff 流程文档和测试报告、gitignore 产物。两次均 22/22，零摘要或文件列表差异，分支和 HEAD 一致。

验收项：
- A1 PASS：仓库根目录运行 `node cloud_front/tests/model-profile-default.cjs`，退出 0，输出 `Model profile defaults: A1 and A2 passed`。审阅断言：默认 ID、未知查询 ID 均与 cloud-deepseek-flash 比较；已知 local-qwen3-1.7b 查询 ID 与原值比较，均实际执行。
- A2 PASS：同一命令、退出 0；审阅断言覆盖无存储、无效 ID、存储读写抛错、选中 ID 失效、删除当前自定义档位，均比较 cloud-deepseek-flash；有效存储 local-qwen3-1.7b 比较原值，删除前另断言确实选中自定义项。
- A3 PASS：上述命令根目录运行退出 0；cloud_front 执行 `npm run type-check` 退出 0。审阅快照中聊天页面差异：引入选择器和布局调整，未改发送请求字段或后端请求契约；本项未连接后端。
- A4 BLOCKED：cloud_front 执行 `npm run type-check` 和 `npm run build-only` 均退出 0，构建输出 `built in 10.70s`，有大 chunk 提示。审阅 vite.config.ts 注册 localEnvPlugin；localEnvPlugin.ts 为 `apply: 'serve'` 和 `configureServer`，无 configurePreviewServer。独立运行 `npm run preview -- --host 127.0.0.1 --port 4179 --strictPort` 退出 1，报 `listen EACCES: permission denied 127.0.0.1:4179`，未启动服务。随后总管告知只读复核发现端点可能违反 Origin 安全契约，要求停止所有端点及服务测试，因此未再尝试端口或发送 HTTP 请求。预期 preview 端点不返回插件 JSON；实际 HTTP 行为未验证，不能判 PASS。该安全疑虑来自总管转述，本轮未独立验证，不在此认定为缺陷。

固定验收缺陷：无已证实功能失败；A4 阻塞登记 Issue 33，复现及影响见上。清单外交付阻断问题：无本测试独立证实的问题。未验证项：A4 preview HTTP；开发过程记录不存在。变异检验未做，任务卡未授权。

问题记录：WindowsApps python 命令退出 1 且无输出；使用 C:\ProgramData\Anaconda3\python.exe 调用 scripts/issue_tracker.py add 成功，问题 33，状态待处理。报告即证据路径。

临时文件、数据及服务状态：未创建临时目录或测试数据，未发送 HTTP 请求或接触密钥；preview 启动失败无服务需停止。构建标准产物及 TypeScript 缓存按任务卡允许保留。风格意见：无。
