# 临时把本机站点放到公网上（面试 / 演示用）

> 适用版本：本次「前后端同源代理」改造之后。**只需要穿透 5173 一个端口**，前后端都在里面。

## 0. 先起服务

```powershell
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
```

脚本结尾会打印：

```
  PC     : http://127.0.0.1:5173
  Phone  : http://192.168.0.105:5173   (same WiFi)
```

确认 Ollama 已启动（AI 对话 / RAG 检索依赖它）：

```powershell
ollama serve        # 另开一个终端；已启动则忽略
ollama list         # 应能看到 qwen3-embedding:4b
```

## 1. 方案 A：同一 WiFi，直接局域网 IP（最省事）

手机连和电脑同一个 WiFi，浏览器打开脚本打印的 `Phone` 地址即可。
登录账号 `admin / 12345678`。

若打不开，多半是 Windows 防火墙拦了 5173（放行命令，管理员终端）：

```powershell
netsh advfirewall firewall add rule name="vite-5173" dir=in action=allow protocol=TCP localport=5173
```

## 2. 方案 B：cpolar 公网穿透（异地 / 手机用 4G）

1. 注册并下载：https://www.cpolar.com/ ，安装后绑定 authtoken（控制台里有）

   ```powershell
   cpolar authtoken <你的token>
   ```

2. 起隧道（**5173，不是 8123**）：

   ```powershell
   cpolar http 5173
   ```

3. 终端会打印一个 `https://xxxx.cpolar.cn` 的随机域名（免费版每次启动都会变），
   手机或任意设备浏览器打开它即可。

### 备选工具

| 工具 | 命令 | 说明 |
|---|---|---|
| Cloudflare 临时隧道 | `cloudflared tunnel --url http://localhost:5173` | 免注册，给 `trycloudflare.com` 域名，国内延迟偏高 |
| ngrok | `ngrok http 5173` | 免费版随机域名，国内速度一般 |
| Tailscale | 两端装客户端 | 不是穿透，是虚拟局域网；手机访问 `http://100.x.x.x:5173`，最稳、无公网暴露 |

## 3. 为什么只穿透 5173 就够了

`vite.config.ts` 里把 `/api` 反向代理到了本机 8123，`request.ts` 的 `DEV_BASE_URL` 为空（走同源）。
所以页面、接口、Cookie 全在同一个 origin 上：

- 穿透一个端口即可，不用管后端；
- 穿透域名是 https 时，不会出现「https 页面请求 http 接口」被浏览器拦截；
- Cookie 是 same-site，`SameSite=Lax` 正常携带，登录态不掉。

## 4. 已知取舍

- **HMR 热更新在 https 穿透域名下会失效**（ws 连不上，控制台报错），手动刷新页面即可，不影响功能。
- **首次 AI 对话 / RAG 检索约 20s**（本机向量化），前端超时已放宽到 60s，别以为卡死了。
- 手机上窄屏（<760px）会隐藏两处：右上角登录态（`.user-login-status`）、
  AI 助手右侧的引用来源面板（`.doc-panel`）。演示 RAG 时建议用横屏或平板。

## 5. 安全（重要）

- 账号是 `admin / 12345678`，穿透期间等于暴露在公网，**演示完立刻关**：

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1
  ```

  并 Ctrl+C 关掉 cpolar 隧道。

## 6. 排障

| 现象 | 原因 / 处理 |
|---|---|
| `Blocked request. This host is not allowed.` | Vite 6 的 host 白名单；已配 `allowedHosts: true`，若仍出现说明服务没重启 |
| 页面能开但接口 502 / 全红 | 后端没起来或没监听 8123，看 `tmp/dev-backend.log` |
| 一直跳登录页 | Cookie 没带上；确认是用同一个穿透域名访问，不要混用 IP 和域名 |
| AI 问答报错 | Ollama 没起，或模型没拉：`ollama pull qwen3-embedding:4b` |
