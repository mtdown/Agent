# Spec Delta

## MODIFIED Requirements

### Requirement: 隧道固定使用 cloudflared

`start-dev.ps1` MUST 默认不启动公网隧道；明确指定 `-Tunnel` 且未指定 `-NoTunnel` 时，MUST 使用本机已安装的 cloudflared 建立指向本地前端端口的公网隧道，不得再探测或调用 cpolar / ngrok。cloudflared 不可用时 MUST 输出安装指引并降级为仅局域网访问，MUST NOT 阻塞后端与前端服务本身的启动。`-NoTunnel` MUST 优先于 `-Tunnel`。

#### Scenario: cloudflared 可用

- **WHEN** 本机 PATH 中存在 cloudflared，指定 `-Tunnel` 且未指定 `-NoTunnel`
- **THEN** 脚本启动 cloudflared 隧道指向本地前端端口，隧道进程 PID 写入 `tmp/tunnel.pid` 供兜底停止脚本使用

#### Scenario: cloudflared 未安装

- **WHEN** PATH 中找不到 cloudflared，指定 `-Tunnel` 且未指定 `-NoTunnel`
- **THEN** 脚本输出 cloudflared 未找到的警告与安装命令（如 winget 安装提示），跳过隧道，后端与前端照常启动，退出流程不受影响

#### Scenario: 默认不启动隧道

- **WHEN** 未指定 `-Tunnel`
- **THEN** 脚本不启动 cloudflared 临时隧道

#### Scenario: 显式关闭优先

- **WHEN** 同时指定 `-Tunnel` 和 `-NoTunnel`
- **THEN** 脚本不启动 cloudflared 临时隧道
