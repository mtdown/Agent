# 临时把本机站点放到公网上（面试 / 演示用）

> 适用版本：本次「前后端同源代理」改造之后。**只需要穿透 3000 一个端口**，前后端都在里面。

## 0. 一键启动

```powershell
# 一键：起服务 + 自动起公网穿透（默认行为，不用加参数）
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1

# 只在本机 / 局域网用，不暴露公网
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1 -NoTunnel
```

默认会按 `cpolar` → `cloudflared` → `ngrok` 的顺序找已安装的工具，
起完隧道后从它的本地 API 读公网地址并打印出来。没装任何工具时只是不启动，
本地和局域网访问照常，不会报错。

```
All done. (admin / 12345678)
  PC     : http://127.0.0.1:3000
  Phone  : http://192.168.0.105:3000   (same WiFi)
  Public : https://xxxx.cpolar.cn
```

可选参数：

| 参数 | 作用 |
|---|---|
| `-NoTunnel` | **只走局域网，不暴露公网**（日常开发用这个） |
| `-Tunnel cpolar\|cloudflared\|ngrok\|none` | 指定工具（默认自动探测）；`none` = 同 `-NoTunnel` |
| `-Public` | 兼容保留，现在默认就会起穿透，不用再加 |
| `-NoPause` | 末尾不等待回车（父脚本调用时用，手动启动不用加） |

⚠️ 默认行为是**暴露到公网**：账号 `admin / 12345678` 会被放到公网上。
日常开发请加 `-NoTunnel`，或演示结束后立刻 `.\stop-dev.ps1`。

脚本跑完会停在 `Press Enter to close this window`，服务在后台继续运行 —— 公网地址在结尾会再打印一次并用绿框标出，不怕被前面的日志刷掉。

隧道 PID 写在 `tmp/tunnel.pid`，`.\stop-dev.ps1` 会连同前后端一起关掉，不用手动收尾。

### start-main-dev.ps1

```powershell
powershell -ExecutionPolicy Bypass -File .\start-main-dev.ps1 -Public -Stay
```

| 参数 | 作用 |
|---|---|
| `-Public` | 允许起公网穿透（**不加则只走局域网**，因为它是日常开发入口） |
| `-Stay` | **不切回 main 分支**，在当前分支启动 |

⚠️ 不加 `-Stay` 时该脚本会 `git checkout main` 并拉取最新代码。
移动端适配和穿透功能目前还在 `feature/mobile-demo-access开发` 分支、**尚未合入 main**，
直接跑会把你切回没有这些功能的 main。演示新功能请务必带 `-Stay`。

## 1. 手动方式（脚本之外的备选）

### 方案 A：同一 WiFi，直接局域网 IP（最省事）

手机连和电脑同一个 WiFi，打开脚本打印的 `Phone` 地址即可，账号 `admin / 12345678`。

若打不开，多半是 Windows 防火墙拦了 3000（管理员终端执行）：

```powershell
netsh advfirewall firewall add rule name="vite-3000" dir=in action=allow protocol=TCP localport=3000
```

### 方案 B：cpolar 手动起隧道

1. 注册并下载：https://www.cpolar.com/ ，安装后绑定 authtoken（控制台里有）

   ```powershell
   cpolar authtoken <你的token>
   ```

2. 起隧道（**3000，不是 8123**）：`cpolar http 3000`

### 其他工具

| 工具 | 命令 | 说明 |
|---|---|---|
| Cloudflare 临时隧道 | `cloudflared tunnel --url http://localhost:3000` | 免注册，给 `trycloudflare.com` 域名，国内延迟偏高（无本地 API，脚本靠抓日志拿地址） |
| ngrok | `ngrok http 3000` | 免费版随机域名，国内速度一般 |
| Tailscale | 两端装客户端 | 不是穿透，是虚拟局域网；手机访问 `http://100.x.x.x:3000`，最稳、无公网暴露 |

## 2. 为什么只穿透 3000 就够了

`vite.config.ts` 里把 `/api` 反向代理到了本机 8123，`request.ts` 的 `DEV_BASE_URL` 为空（走同源）。
所以页面、接口、Cookie 全在同一个 origin 上：

- 穿透一个端口即可，不用管后端；
- 穿透域名是 https 时，不会出现「https 页面请求 http 接口」被浏览器拦截；
- Cookie 是 same-site，`SameSite=Lax` 正常携带，登录态不掉。

另外 `vite.config.ts` 里有 `allowedHosts: true` —— Vite 6 默认只放行 localhost / 纯 IP 的 Host，
少了这行，穿透域名会被拦成 `Blocked request. This host is not allowed.`

## 3. 已知取舍

- **HMR 热更新在 https 穿透域名下会失效**（ws 连不上，控制台报错），手动刷新页面即可，不影响功能。
- **首次 AI 对话 / RAG 检索约 20s**（本机向量化），前端超时已放宽到 60s，别以为卡死了。
- 免费版 cpolar 的域名**每次启动都会变**，重启就要重新发地址。
- 手机上窄屏（<760px）会隐藏两处：右上角登录态（`.user-login-status`）、
  AI 助手右侧的引用来源面板（`.doc-panel`）。演示 RAG 时建议用横屏或平板。

## 4. 安全（重要）

- 账号是 `admin / 12345678`，穿透期间等于暴露在公网，**演示完立刻关**：

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1
  ```

  脚本会一并停掉后端、前端和隧道。

## 5. 排障

| 现象 | 原因 / 处理 |
|---|---|
| `Blocked request. This host is not allowed.` | Vite 6 的 host 白名单；确认 `allowedHosts: true` 已生效，并重启 dev server |
| 脚本提示 `No tunnel tool found` | 没装 cpolar/cloudflared/ngrok，或没加进 PATH |
| 隧道起来了但没打印 Public 地址 | 手动看最小化的工具窗口，或 `Get-Content tmp\tunnel-cpolar.log` |
| 页面能开但接口 502 / 全红 | 后端没起来或没监听 8123，看 `tmp/dev-backend.log` |
| 一直跳登录页 | Cookie 没带上；确认是用同一个穿透域名访问，不要混用 IP 和域名 |
| AI 问答报错 | Ollama 没起，或模型没拉：`ollama pull qwen3-embedding:4b` |
| 服务端口不对 | 前端是 **3000**（`start-dev.ps1` 的 `$frontPort`），不是 vite 默认的 5173 |
