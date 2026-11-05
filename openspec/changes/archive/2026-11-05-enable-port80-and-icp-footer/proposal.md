## Why

2026-11-05 收到阿里云 ICP 备案通过通知（订单号 2032869637879，网站备案号 `渝ICP备2026028042号-1`，网站名称「自用测试」，域名 `cloudwiki.club`，负责人李海东）。通知提出三项要求：首页底部中间展示备案号并链接 `http://beian.miit.gov.cn/`、30 天内把域名解析到阿里云内地服务器、开通后 30 日内办理公安备案。

取证结果（2026-11-05，只读核实）：

| 检查项 | 实测 | 结论 |
|---|---|---|
| `cloudwiki.club` / `www.cloudwiki.club` 权威 A 记录 | 均指向已备案 ECS；NS 为 `dns27/28.hichina.com`，SOA serial `2026101211`，TTL 600s | **解析要求已满足**，无需改动 |
| `http://cloudwiki.club`（默认 80） | HTTP 000，8s 超时 | 不通 |
| `http://cloudwiki.club:8080` | HTTP 200 | 站点本身可用 |
| `cloud_front/src/layouts/BasicLayout.vue:10-12` footer | 只有「项目文件」链接 | **备案号缺失** |

断点定位：容器内 Nginx `listen 8080`、Compose `"${HTTP_PORT:-8080}:8080"`、`.github/workflows/deploy.yml:106` 每次部署都写 `HTTP_PORT=8080`。因此不带端口访问必然失败；备案通过后应切到 80。

同时发现 `deploy/README.md`「备案通过后切 80/443」的第 2 步写「改 ports 为 `"80:80"`」，与 `nginx.conf` 的 `listen 8080` 直接冲突：照做会形成宿主机 80 → 容器内 80（无人监听），站点全量不可用。第 3 步的 `certbot --nginx` 也未验证（无 443 server 块、compose 未映射 443、宿主机 certbot 看不到容器内 bind mount 的配置）。本 change 一并修正文档，但**不实施 HTTPS**。

## What Changes

| # | 变更 | 落点 |
|---|---|---|
| C1 | CI 生成 `.env` 时把对外端口从 8080 改为 80 | `.github/workflows/deploy.yml:106` |
| C2 | 同步端口默认值与注释，消除「改成 80:80」的错误指引 | `deploy/.env.example`、`deploy/docker-compose.yml`、`deploy/nginx.conf` |
| C3 | 重写 README 的备案后切换章节，写明前置条件与顺序 | `deploy/README.md` |
| C4 | 全局 footer 增加完整备案号与工信部链接 | `cloud_front/src/layouts/BasicLayout.vue:10-12` |
| C5 | 建立本 change 的规格与任务工件 | `openspec/changes/enable-port80-and-icp-footer/` |

## Capabilities

### New Capabilities

- `site-access-and-filing`: 规定备案域名的默认 HTTP 端口由单一配置来源控制、容器内监听端口保持不变，以及全局 footer 必须展示完整备案号并链接工信部。

### Modified Capabilities

（无。刻意不 MODIFIED `wiki-layout`：MODIFIED 是整块替换，原 spec 的 5 个 Requirement 与全部 Scenario 必须逐字保留，风险高于收益。）

## Acceptance Criteria

- **AC1** `.github/workflows/deploy.yml` 生成 `.env` 的语句为 `printf 'HTTP_PORT=80\n'`，且 `HTTP_PORT` 仍在 `for required in ...` 自检列表内。
- **AC2** `deploy/docker-compose.yml` 的 nginx ports 保持 `"${HTTP_PORT:-8080}:8080"`；`deploy/nginx.conf` 保持 `listen 8080;`。两者均**不出现** `"80:80"`。
- **AC3** `cloud_front/src/layouts/BasicLayout.vue` 的 footer 含字面量 `渝ICP备2026028042号-1`，且该文本位于 `href="http://beian.miit.gov.cn/"` 的 `<a>` 内；原有「项目文件」链接保持不变。
- **AC4** `deploy/README.md` 不再含 `"80:80"` 指引与可直接照抄的 `certbot --nginx` 命令，并显式写出「先在安全组放行 TCP 80，再合并触发部署」的顺序。
- **AC5** `openspec validate enable-port80-and-icp-footer --strict` 退出码 0。
- **AC6** 本任务分支不在 `.github/workflows/deploy.yml` 的 `on.push.branches`（只有 `main` 与 `feature/deploy-cicd开发`）内，推送本分支**不触发**线上部署。
- **AC7** `git status` 中除本 change 的 10 个文件外无其它改动。

## Non-goals

- 不改 DNS 解析记录（已生效且满足备案核查）
- 不实施 HTTPS / 443 / certbot（无 443 server 块，另行立项）
- 不改 `server_name _`、不改容器内 `listen 8080`
- 不放行阿里云安全组、不改服务器 `.env`（属负责人控制台操作，且需先放行 80）
- 不改后端、Agent、数据库、RAG 链路、Nginx 代理规则
- 不合并 main、不触发线上部署
- 不代办公安备案（另行办理）

## Impact

- 影响面：CI/CD 生成的 `.env`（**L2 判据**）、部署文档、前端全局 footer。**后端 / 数据库 / 公开 API 签名 / 权限模型零改动**。
- 风险：`HTTP_PORT=80` 合并触发部署后，若安全组未放行 TCP 80，站点将从「`:8080` 可用」变为「默认端口不可用」。因此 AC4 把顺序写入文档，且本 change 不合并 main。
- 容器编排关键事实：宿主机端口与容器内监听端口是两个值；`nginx.conf` 是 bind mount，换文件需 `nginx -s reload`（`deploy.sh` 已处理）。
- 回滚方案：把 `deploy.yml:106` 改回 `printf 'HTTP_PORT=8080\n'` 并重新部署即可恢复 `:8080` 访问；前端 footer 与文档改动用 `git revert` 单文件还原；DNS 与服务器配置未动，无需回滚。
