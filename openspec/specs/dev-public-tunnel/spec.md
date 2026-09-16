# dev-public-tunnel Specification

## Purpose
定义本地开发脚本的一键公网隧道能力：固定使用 cloudflared 暴露本地前端端口，公网地址醒目展示，并保证隧道进程与服务同启同停。

## Requirements

### Requirement: 隧道固定使用 cloudflared

`start-dev.ps1` MUST 使用本机已安装的 cloudflared 建立指向本地前端端口的公网隧道，不得再探测或调用 cpolar / ngrok。cloudflared 不可用时 MUST 输出安装指引并降级为仅局域网访问，MUST NOT 阻塞后端与前端服务本身的启动。

#### Scenario: cloudflared 可用

- **WHEN** 本机 PATH 中存在 cloudflared，且未指定 `-NoTunnel`
- **THEN** 脚本启动 cloudflared 隧道指向本地前端端口，隧道进程 PID 写入 `tmp/tunnel.pid` 供兜底停止脚本使用

#### Scenario: cloudflared 未安装

- **WHEN** PATH 中找不到 cloudflared，且未指定 `-NoTunnel`
- **THEN** 脚本输出 cloudflared 未找到的警告与安装命令（如 winget 安装提示），跳过隧道，后端与前端照常启动，退出流程不受影响

### Requirement: 公网地址与转发端口必须醒目展示

隧道启动后，`start-dev.ps1` MUST 从 cloudflared 日志中抓取 trycloudflare.com 公网 URL，并在控制台醒目（绿色高亮框）展示该 URL 及被转发的本地端口；在限定时间内无法取得 URL 时 MUST 输出排查提示与日志文件路径，MUST NOT 伪造成功展示。

#### Scenario: 成功取得公网地址

- **WHEN** cloudflared 在等待窗口内输出含 trycloudflare.com 域名的日志
- **THEN** 控制台以绿色高亮展示公网 URL，同时标明该隧道转发的是本地前端端口 3000

#### Scenario: 超时未取得公网地址

- **WHEN** 等待窗口结束后仍未在日志中匹配到公网 URL
- **THEN** 脚本输出未获取到公网地址的提示与 cloudflared 日志文件路径，不打印成功样式的公网地址

### Requirement: 启动窗口回车即全停

`start-dev.ps1` 在服务与隧道启动完成并展示地址后，MUST 等待用户按回车；用户按回车后 MUST 停止本次启动的后端（8123）、前端（3000）与 cloudflared 隧道进程，逐项输出停止结果。用户直接关闭窗口（不按回车）时 MUST 保留所有服务继续运行。

#### Scenario: 按回车停止全部服务

- **WHEN** 服务启动完成，用户在启动窗口按回车
- **THEN** 后端、前端、隧道进程依次被停止，停止后端口不再处于监听状态，脚本正常退出

#### Scenario: 直接关闭窗口保留服务

- **WHEN** 服务启动完成，用户直接关闭启动窗口
- **THEN** 后端、前端、隧道进程不受影响继续运行，可由 `stop-dev.ps1` 兜底回收

### Requirement: 必须提供关闭隧道的逃生口

`start-dev.ps1` MUST 提供参数（`-NoTunnel`）在完全不建立公网隧道的情况下启动服务，并在控制台明确说明本次未开启公网暴露。

#### Scenario: 指定不使用隧道

- **WHEN** 以 `-NoTunnel` 参数启动
- **THEN** 不启动任何隧道进程，控制台输出仅局域网访问的说明，本地与局域网地址正常展示
