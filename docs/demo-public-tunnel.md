# 临时把本机站点放到公网上（面试 / 演示用）

> 适用版本：隧道统一为 cloudflared 之后。**只需要穿透 3000 一个端口**，前后端都在里面。

## 0. 一键启动

```powershell
# 一键：起服务 + 自动起 cloudflared 隧道（默认行为，不用加参数）
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1

# 只在本机 / 局域网用，不暴露公网
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1 -NoTunnel
```

隧道固定使用本机已安装的 **cloudflared**（免注册 quick tunnel），公网地址从它的日志里抓出来
并打印。没装 cloudflared 时只是不起隧道，本地和局域网访问照常，不会报错（脚本会给出
`winget install Cloudflare.cloudflared` 的安装提示）。

```
All done. (admin / 12345678)
  PC     : http://127.0.0.1:3000
  Phone  : http://192.168.0.105:3000   (same WiFi)
  Public : https://xxxx.trycloudflare.com
================================================================
  Public URL (share this) : https://xxxx.trycloudflare.com
  Forwards to local port  : 3000 (frontend; /api proxied to backend)
  (random domain changes on every restart)
================================================================
Press Enter to STOP all services (backend + frontend + tunnel).
Close this window directly to KEEP everything running
(recover later with .\stop-dev.ps1).
```

**收尾语义**：在启动窗口**按回车 = 停止全部服务**（后端 + 前端 + 隧道）；
**直接关窗口 = 服务全部保留**，之后用 `.\stop-dev.ps1` 回收。

另外：启动前如果 8123 / 3000 被上次遗留的服务占着，脚本会自动清理后继续，不用先手动 stop。

可选参数：

| 参数 | 作用 |
|---|---|
| `-NoTunnel` | **只走局域网，不暴露公网**（日常开发用这个） |
| `-Preview` | **公网演示建议加**：先打包再提供服务，而不是跑 dev server |
| `-NoPause` | 末尾不等回车、不停服务（父脚本调用时用，手动启动不用加） |

### 为什么公网演示建议加 `-Preview`

Vite 开发服务器是**未打包**的：浏览器要按模块发**几百个独立请求**，每个请求都要在隧道里
往返一次，延迟叠加后就是"完全进不去"（现象：地址能打开但一直转圈，或直接超时）。

`-Preview` 会先 `npm run build`，再用 `vite preview` 提供打包产物，请求数从几百降到个位数：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1 -Preview
```

代价：改代码后要重跑脚本（热更新失效），纯演示场景无所谓。

⚠️ 默认行为是**暴露到公网**：账号 `admin / 12345678` 会被放到公网上。
日常开发请加 `-NoTunnel`，或演示结束后在启动窗口按回车（或跑 `.\stop-dev.ps1`）。

### 慢 / 打不开的排查顺序

1. **先确认局域网地址能不能开**：`http://192.168.0.105:3000`。局域网秒开而公网很慢，
   说明瓶颈在隧道链路本身，此时优先用局域网演示，或改用 Tailscale（点对点，不走免费隧道）。
2. 公网一定要加 `-Preview`，否则几百个模块请求会拖慢首屏。

隧道 PID 写在 `tmp/tunnel.pid`，按回车或 `.\stop-dev.ps1` 都会连同前后端一起关掉，不用手动收尾。

### start-main-dev.ps1（main 验收）

```powershell
powershell -ExecutionPolicy Bypass -File .\start-main-dev.ps1
```

| 参数 | 作用 |
|---|---|
| `-NoTunnel` | 不起公网隧道（**默认会起**，和 `start-dev.ps1` 一致） |
| `-Stay` | **不切回 main 分支**，在当前分支启动 |

⚠️ 不加 `-Stay` 时该脚本会 `git checkout main` 并拉取最新代码 —— 演示尚未合入 main 的功能时
务必带 `-Stay`。启动后的回车全停、隧道展示与 `start-dev.ps1` 完全一致。

验收完想单独回收服务：

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-main-dev.ps1
```

它等价于在项目根目录执行 `stop-dev.ps1`（停 8123 / 3000 + 隧道）。

## 1. 手动方式（脚本之外的备选）

### 方案 A：同一 WiFi，直接局域网 IP（最省事）

手机连和电脑同一个 WiFi，打开脚本打印的 `Phone` 地址即可，账号 `admin / 12345678`。

若打不开，多半是 Windows 防火墙拦了 3000（管理员终端执行）：

```powershell
netsh advfirewall firewall add rule name="vite-3000" dir=in action=allow protocol=TCP localport=3000
```

### 方案 B：手动起 cloudflared 隧道

```powershell
cloudflared tunnel --url http://localhost:3000
```

免注册，输出里找 `trycloudflare.com` 域名。Ctrl+C 即关。

### 其他工具

| 工具 | 命令 | 说明 |
|---|---|---|
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

- **HMR 热更新在 https 穿透域名下会失效**（ws 连不上，控制台报错），手动刷新页面即可，不影响功能（`-Preview` 模式无此问题）。
- **首次 AI 对话 / RAG 检索约 20s**（本机向量化），前端超时已放宽到 60s，别以为卡死了。
- cloudflared quick tunnel 的域名**每次启动都会变**，重启就要重新发地址。
- 手机上窄屏（<760px）会隐藏两处：右上角登录态（`.user-login-status`）、
  AI 助手右侧的引用来源面板（`.doc-panel`）。演示 RAG 时建议用横屏或平板。

## 4. 安全（重要）

- 账号是 `admin / 12345678`，穿透期间等于暴露在公网，**演示完立刻关**：
  在启动窗口按回车，或执行：

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1
  ```

  两者都会一并停掉后端、前端和隧道。

## 5. 排障

| 现象 | 原因 / 处理 |
|---|---|
| `Blocked request. This host is not allowed.` | Vite 6 的 host 白名单；确认 `allowedHosts: true` 已生效，并重启 dev server |
| 脚本提示 `cloudflared not found in PATH` | 没装 cloudflared 或没加进 PATH：`winget install Cloudflare.cloudflared` |
| 隧道起来了但没打印 Public 地址 | 手动看最小化的 cloudflared 窗口，或 `Get-Content tmp\tunnel-cloudflared.err.log` |
| 页面能开但接口 502 / 全红 | 后端没起来或没监听 8123，看 `tmp/dev-backend.log` |
| 一直跳登录页 | Cookie 没带上；确认是用同一个穿透域名访问，不要混用 IP 和域名 |
| AI 问答报错 | Ollama 没起，或模型没拉：`ollama pull qwen3-embedding:4b` |
| 服务端口不对 | 前端是 **3000**（`start-dev.ps1` 的 `$frontPort`），不是 vite 默认的 5173 |
| 上次的服务还占着端口 | 直接重跑 `start-dev.ps1` 即可，会自动清理遗留服务后继续启动 |
