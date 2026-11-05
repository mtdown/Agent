# 阶段 9 第 3 轮独立测试报告

Change ID／阶段／任务／轮次：add-model-selector／9／9.1–9.4 与 Issue 37／第 3 轮
测试时间：2026-11-04 至 2026-11-05（Asia/Shanghai）；测试实例：/root/final_retest
总体结论：PASS（仅限任务卡固定验收范围）

环境与基线：Windows；PowerShell 5.1.19041.6456；分支 feature/model-selector开发；基线 HEAD f5ecb69bc2ccc292c44fa7ea1ee372abaa34f612。未切换或运行基线。仅隔离模拟，无真实服务、网络请求或真实密钥访问。开发过程及修改事实无独立持久化记录可核验；据代码、测试和快照独立核对，不推断开发过程。

被测快照：测试前后使用 git branch --show-current、git rev-parse HEAD、Get-FileHash -Algorithm SHA256 核对，分支、HEAD 和下列摘要均一致；清单 stage-9-round-3.json 为 7cf6baa058e6b648dcd2671fd87093db0035fa4bf00bbfa337e36e43f295429f，补丁 stage-9-round-3.patch 为 47dd0edd51dd8d937bcd78a510a69ee875777c6e1dfd255c587301bf93710f36。逐字节比较 git diff -- start-dev.ps1 与补丁首段一致；逐个比较补丁五个 /dev/null 新增文件全文与工作区一致（允许附录分隔换行）。工作区其他已有变更不属于阶段 9 六文件快照。

- cloud_front/vite/localEnvPlugin.ts：482e62fb7aac6c5712c66ccb24ad344b8380d1ce901ad0da00ac6e588665ce37
- cloud_front/src/constants/modelProfiles.ts：801ab07660061aaef5a23ca4a9f462021c0fb1019679223e4e321caab9e3e139
- cloud_front/src/components/ModelSelector.vue：c5ca34b093b4e27dbf336b00ca19a954cdf0b9958d3616249e32361e0eef58ca
- start-dev.ps1：ad0f1f3330db434b89a50ecb395e67975bfe8233b6c45238424215865b8ad1dd
- cloud_front/tests/local-env-isolation.cjs：0b1f8bc4b6e6e94e4d1039855c150c23f58b26ad6707e262e93312acd79ed482
- scripts/test-start-dev-tunnel.ps1：85868acdc4d0d40134b135048edc8aab9f39517c942d8d581299734b1f19a23d

验收结果：

- A1 PASS。根目录 node cloud_front/tests/local-env-isolation.cjs，退出 0。预期零、一、多次同名键各保留一条新值并保留其他行及顺序；实际三个占位夹具的完整输出与预期严格相等，断言一次临时写入及重命名。
- A2 PASS。上述 Node 命令退出 0，前端 npm run type-check、npm run build-only 均退出 0。预期无 Origin 和自定义头的占位请求可写，不允许的键、空值、换行值拒绝，标志为 1 不注册，端点不可用禁用密钥区，生产资产无端点；实际合成请求与注册断言通过；组件提取函数模拟 404 后 envWritable 为 false，模板含提示及禁用绑定；递归检查 dist 全部 128 文件，端点 /__cw/local-env 命中 0。UI 为模拟及静态核验，未使用真实浏览器/服务。
- A3 PASS。根目录 powershell -NoProfile -File scripts/test-start-dev-tunnel.ps1 退出 0，PowerShell 5.1 解析通过。默认、NoTunnel、Tunnel、同时指定的前端标志经模拟启动断言分别为 0、0、1、0；隧道调用次数分别 0、0、1、0。实际进程由桩替代，未启动服务。
- A4 PASS。上述 Node 和 PowerShell 命令、前端 type-check 和 build-only 均退出 0；生产构建端点命中 0。构建提示存在大于 500 kB 的 chunk，不影响本项验收。
- Issue 37 定向复测 PASS。隔离测试对 JSON null、false、42、字符串和数组逐一断言返回 400、响应在 1 秒内完成、内存文件不变且零写操作；Node 命令正常退出 0，无未处理拒绝导致的进程失败。代码亦对非对象及数组在读取键前返回 400。未执行真实子进程服务器或网络测试。

固定验收缺陷：无。清单外交付阻断问题：未发现。未验证项与原因：未运行真实服务、真实子进程前端启动、真实网络与浏览器；任务卡禁止启动服务和连接网络。未做变异检验，任务卡未授权。问题记录：Issue 37 复测通过，见本报告与原 stage-9-retest-2.md 的索引；Issue 34、35 未重置。临时文件与服务：无新建临时目录、未启动服务；保留允许的标准构建产物及 TypeScript 缓存，未删除目录。风格意见：无。
