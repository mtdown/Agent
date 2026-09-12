# dev-script-reliability Delta

## ADDED Requirements

### Requirement: Preview 模式前端构建不得因日志重定向失败

`start-dev.ps1` 在 `-Preview` 模式下执行前端构建时，MUST 将标准输出与标准错误重定向到两个不同的文件，保证 Windows PowerShell 5.1 下 `Start-Process` 不因"重定向目标相同"抛出异常。构建失败时 MUST 检测不到产物并给出指向构建日志的明确报错后退出。

#### Scenario: Preview 构建正常完成

- **WHEN** 执行 `start-dev.ps1 -Preview`，前端构建成功产出 `dist/index.html`
- **THEN** 脚本不因日志重定向报错，继续用打包产物启动前端预览服务

#### Scenario: Preview 构建失败

- **WHEN** 前端构建命令失败，`dist/index.html` 未生成
- **THEN** 脚本输出构建失败并指向 stdout / stderr 构建日志文件，退出码非 0

### Requirement: 显式指定隧道工具时公网地址必须来自所选工具

`start-dev.ps1` 通过 `-Tunnel` 显式选择隧道工具时，上报的公网地址 MUST 来自该工具自身；选择 cloudflared 时 MUST 跳过对 cpolar / ngrok 本地管理 API（9200 / 4040 端口）的探测，仅从 cloudflared 输出中提取 `*.trycloudflare.com` 地址，避免被本机常驻的 cpolar 服务抢答。

#### Scenario: 显式 cloudflared 且 cpolar 服务常驻

- **WHEN** 以 `-Tunnel cloudflared` 启动，且本机 9200 端口有常驻 cpolar 服务的 API 在监听
- **THEN** 脚本启动 cloudflared 隧道，最终打印的 Public URL 是 `*.trycloudflare.com` 域名，而不是 cpolar 的地址

#### Scenario: cloudflared 未产出地址

- **WHEN** 以 `-Tunnel cloudflared` 启动，cloudflared 进程启动后在超时时间内未输出任何 `trycloudflare.com` 地址
- **THEN** 脚本明确提示未获取到公网地址并指向 cloudflared 日志，不得打印其他工具的地址
