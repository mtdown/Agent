# 阶段 9 任务清单

## 9.1 密钥行覆盖
目标：覆盖同名键并去重，不存在则新增一次。允许文件：`cloud_front/vite/localEnvPlugin.ts`。依赖：无。验收 A1：同名键出现零、一、多次时，写入后各恰好一条且为新值；保留其他键、注释和相对顺序；使用无真实密钥的占位数据。verify：根目录 `node cloud_front/tests/local-env-isolation.cjs`，相关断言通过且退出 0。

## 9.2 开发端点与界面
目标：删除来源、Origin、自定义头检查及无用请求头；启动标志启用时不注册密钥端点，界面检测不可用后禁用密钥区。允许文件：`cloud_front/vite/localEnvPlugin.ts`、`cloud_front/src/constants/modelProfiles.ts`、`cloud_front/src/components/ModelSelector.vue`。依赖：9.1。验收 A2：端点启用时，不含 Origin 和自定义头的有效占位请求按键和值验证并可写入隔离夹具；不允许的键、空值和换行值拒绝；隧道标志启用时不注册端点；端点不可用时界面禁用密钥区；生产构建资产不包含端点字符串。verify：根目录 `node cloud_front/tests/local-env-isolation.cjs`；`cd cloud_front; npm run type-check; npm run build-only`；核对界面处理与构建产物。预期断言和两个命令退出 0，生产构建资产命中端点字符串 0。

## 9.3 启动脚本信号
目标：启动前端进程前依据有效隧道选项设置端点禁用标志，不污染已有密钥。允许文件：`start-dev.ps1`。依赖：9.2。验收 A3：默认、`-NoTunnel`、`-Tunnel`、两者同时指定，传递给前端的禁用标志分别为 0、0、1、0；PowerShell 5.1 解析通过。verify：根目录 `powershell -NoProfile -File scripts/test-start-dev-tunnel.ps1`；预期退出 0 并断言所有状态，不启动服务。

## 9.4 隔离验证
目标：用内存隔离文件系统与合成请求覆盖上述功能，不连接网络、不写真实密钥，扩展隧道信号测试。允许文件：`cloud_front/tests/local-env-isolation.cjs`、`scripts/test-start-dev-tunnel.ps1`。依赖：9.1–9.3。验收 A4：根目录 `node cloud_front/tests/local-env-isolation.cjs` 和 `powershell -NoProfile -File scripts/test-start-dev-tunnel.ps1` 均退出 0；前端目录 `npm run type-check` 与 `npm run build-only` 均退出 0；生产资产中端点字符串命中数为 0；不启动真实服务或访问真实密钥。
