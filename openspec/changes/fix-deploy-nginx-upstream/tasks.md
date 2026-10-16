# Tasks

## 1. Proxy recovery

- [x] 1.1 Add a failing deployment regression check for dynamic backend resolution and Nginx configuration reload; verify it fails against the current deployment files.
      `deployProxy.test.mjs`（真实容器）+ `agentDeployment.test.mjs` 新增 6 条断言。
      红态证据见 2.1 的变异检验（写死 upstream / 去掉 resolver / 去掉 reload / nginx.conf 移出备份清单）。
- [x] 1.2 Make Nginx resolve the current backend container without changing `/api` routing; verify the regression check and `nginx -t` pass.
      `deploy/nginx.conf` 加 `resolver 127.0.0.11 valid=10s ipv6=off;` + `set $backend_upstream "backend:8123";`
      + `proxy_pass http://$backend_upstream;`。容器内 `nginx -t` 通过，回归测试断言后端回显 `B:/api/ping`（路径未被改写）。
- [x] 1.3 Validate and reload the mounted Nginx configuration during deployment and show proxy diagnostics on health failure; verify the regression check and shell syntax pass.
      `deploy.sh` 新增 `[4/5]`：`nginx -t` 失败即终止并打印代理日志，通过则 `nginx -s reload`；
      健康检查失败分支追加 `docker compose logs --tail=60 nginx`。`bash -n` 通过。
- [x] 1.4 Update deployment troubleshooting documentation and IssueLog.xlsx with the incident, cause, and verification status; inspect the saved workbook row.
      `deploy/README.md` 排障表 + 「已踩过的坑」第 5 条 + 回滚章节；
      `IssueLog.xlsx` 追加第 236（事故）、237（本机 spawnSync EBUSY）行，已回读确认。

## 2. Verification and handoff

- [x] 2.1 Run deployment regression, OpenSpec strict validation, and 2–3 mutation checks; record red/green outcomes.

  | 检查 | 结果 |
  |---|---|
  | `node agentDeployment.test.mjs` | 绿：`Agent deployment contract: passed` |
  | `node deployProxy.test.mjs` | 绿：`backend 172.21.0.2 -> 172.21.0.4, proxy never restarted` |
  | `openspec validate fix-deploy-nginx-upstream --strict` | 绿：`Change is valid` |
  | `bash -n deploy/deploy.sh` / `rollback.sh` | 绿 |
  | 变异 1：`proxy_pass` 改回 `http://backend:8123` | 红 → 恢复后绿。容器测试精确失败在「stale 172.21.0.2」，即线上事故形态；静态测试失败在 `proxy_pass must use a variable` |
  | 变异 2：注释掉 `resolver` | 红 → 恢复后绿。**顺带暴露静态断言漏洞**：注释里的 `resolver 127.0.0.11` 也能满足正则，已改为锚定行首 |
  | 变异 3：注释掉 `deploy.sh` 的 `nginx -s reload` | 红 → 恢复后绿。**同样暴露漏洞**，已改为断言前先剥掉注释行 |
  | 变异 4：`nginx.conf` 移出 workflow 备份清单与 rollback 恢复清单 | 红 → 恢复后绿 |
  | 线上取证 | nginx 日志 upstream `172.18.0.4` vs `cw-backend` 实际 `172.18.0.6` → 判定旧地址；reload 后 `/api/user/get/login` 由 502 变 200 |

- [x] 2.2 Perform AGENTS.md §8 assumption, production failure, mentor question, and diff checks; record conclusions here.

  **8.2 隐式假设（每条注明不成立时的表现）**
  1. 容器内 `127.0.0.11` 可用（Docker 内嵌 DNS，nginx 在 `cw` 桥接网络）→ 不成立则解析不到 `backend`，`/api` 全 502。变异 2 已实测就是这表现。
  2. 变量形式的 `proxy_pass` 保留原始 URI → 不成立则 `/api` 前缀被吞、Spring 404。已用后端回显 `$request_uri` 断言 `/api/ping` 原样到达。
  3. compose 服务名是 `nginx`、且执行时容器已就绪 → 不成立则 `deploy.sh` 在 `[4/5]` 退出。已加 15 次 × 2s 的 `exec true` 等待。
  4. 官方镜像 PID 1 是 nginx master，`nginx -s reload` 可用 → 成立（nginx:1.27-alpine）。
  5. `valid=10s` 短于后端启动窗口 → 最多 10s 追上新 IP，健康检查 300s 有充足余量。
  6. `nginx.conf.bak` 在回滚时存在 → **首次部署没有 .bak**，`rollback.sh` 用 `if [ -f ]` 跳过，行为与改动前一致。
  7. GitHub runner 上 `docker network create` / `docker run` 可用 → 不成立则新增的容器回归步骤红灯并阻塞部署（见下方风险）。

  **8.2 上线后最可能怎么出问题（按可能性排序）**
  1. **CI 的容器回归步骤因 runner 环境抖动而红灯**（镜像拉取失败 / docker 网络受限）。表现：job 停在「校验代理动态解析」。提前发现：首次跑观察该 step 输出；若连续两次非因配置问题失败，就把它降级为手动脚本。**这是本次改动引入的新失败面，需要负责人知晓。**
  2. 后端已拿到新 IP 但端口还没监听完 → 短暂 connection refused，`valid=10s` 自愈，健康检查窗口够。
  3. `nginx.conf` 语法错误 → 被 `nginx -t` 拦下并 `exit 1`，旧版本仍在运行（失败可见，不静默）。
  4. 回滚把 `nginx.conf` 退回旧版（可能没有 resolver），此时若后端 IP 又变了会再 502 → 已由 `rollback.sh` 结尾同时探 `/api` 暴露，不会表现为「回滚成功」。

  **8.2 Mentor 三问**
  1. 为什么不用 `docker compose up -d --force-recreate nginx`？→ 会中断正在访问的前端，而且只治「部署时」这一个场景；后端单独重启/崩溃恢复换 IP 仍会挂。动态解析两个场景都覆盖。
  2. `valid=10s` 会不会放大 DNS 压力？→ 每个 worker 每 10s 一次 A 记录查询打到 Docker 内嵌 DNS，量级可忽略；换来的是换 IP 后 10s 内自愈。
  3. 怎么证明是修复有效，而不是「reload 一下刚好好了」？→ `deployProxy.test.mjs` 用真实容器复现「后端换 IP + 代理不重启」，且对修复本身做了 4 项变异检验，写死 upstream 时精确失败在「后端已换 IP、代理没跟上」。

  **8.3 提交前三问**
  - 对外签名变了吗？→ 没有。公开端口、URL、`/api` 路径、容器名、环境变量、配置键均未变；只改容器内代理的解析方式。
  - 隐式假设是什么？→ 见上，逐条已验。
  - 测试能失败吗？→ 能。4 项变异检验全部由绿转红，恢复后回到绿。

  **8.4 diff 面**：本次改动文件为 `deploy/nginx.conf`、`deploy/deploy.sh`、`deploy/rollback.sh`、
  `deploy/README.md`、`.github/workflows/deploy.yml`、`agentDeployment.test.mjs`、`deployProxy.test.mjs`（新增）、
  `IssueLog.xlsx`、`openspec/changes/fix-deploy-nginx-upstream/*`。**无 SQL、无表结构变更**；
  shell 改动集中在「校验 → 热加载 → 失败时打日志」，风险面是「配置未生效」而非数据。

- [ ] 2.3 人工验收（待负责人）。本次改动不触碰后端 Java 与前端 Vue，本地 `start-dev.ps1` 起不到验证作用，
      故未启动本地服务；需要负责人确认的是部署链路本身：
      1. 触发一次部署，观察 Actions 中「校验代理动态解析（容器回归）」与 `deploy.sh` 的 `[4/5]` 是否输出 `代理配置已生效`；
      2. 部署完成后访问 `http://8.137.56.125:8080`，登录并发一条 `/aiAssistant` 问题（流式回答 + 历史记录）；
      3. （可选）在预演环境执行 `bash rollback.sh`，确认结尾同时打印首页与接口返回码。

## 3. 已拍板的决策（2026-10-16）

| 决策 | 选择 | 理由 |
|---|---|---|
| CI runner 标签 | **Pin 到 `ubuntu-24.04`** | 2026-10-19 起 `ubuntu-latest` 漂到 Ubuntu 26.04，Compose v2 2.38 → v5 1.3；而 `agentDeployment.test.mjs` 依赖 `docker compose config --format json` 的输出结构。Pin 把漂移时机从「GitHub 决定」改成「我们决定」 |
| 容器回归测试是否留在 CI | **留在 CI（阻塞）+ 加环境预检** | 保持自动护栏；预检让「Docker 不可用」与「配置写错」可区分，不再抛难懂的 spawn 报错 |

补充事实（核实过，非推测）：
- **CI 本来就依赖 Docker daemon，不是本次引入**：`docker compose config` 在 daemon 不可用时同样失败
  （实测 `DOCKER_HOST` 指向无效地址即 FAIL），而 `agentDeployment.test.mjs` 改动前就在调它。
- GitHub-hosted `ubuntu-24.04` 镜像自带 Docker 28.0.4 + Compose v2 2.38.2，无需额外配置；
  换本地机器不影响 CI。

环境预检已实现于 `deployProxy.test.mjs`（`docker info` 探测），实测无 daemon 时输出：

```
容器回归测试需要可用的 Docker daemon，当前连不上。
  原因：docker info --format {{.ServerVersion}} exited 1
  本地：请先启动 Docker Desktop。
  CI：请确认 runner 镜像自带 Docker（ubuntu-24.04 镜像自带）。
```

加预检后重跑了变异 1（写死 upstream），两个测试仍精确转红，恢复后转绿 ⇒ 护栏未被削弱。

## 4. 生产推送（2026-10-16 21:20 已执行）

已把永久修复推到 `8.137.56.125:/opt/cloudwiki`，**生产不再是临时恢复状态**。

| 步骤 | 结果 |
|---|---|
| 备份 `nginx.conf` | `nginx.conf.bak-pre-dynamic-dns-20261016` |
| 用临时容器离线 `nginx -t` | 通过（避免带病重建把生产打挂） |
| `docker compose restart nginx` 重建挂载 | 容器内 inode 与宿主机一致（`267033`），配置已换成新版 |
| `deploy.sh` / `rollback.sh` 同步 | 均已推送，`bash -n` 通过 |

**生产验证（决定性）**：用占位容器抢走旧地址，逼后端换 IP ——

```
旧后端 IP=172.18.0.6 / 占位容器=172.18.0.6 / 新后端 IP=172.18.0.7
Nginx 全程不 reload、不重启 → 约 10 秒后 api=200
```

旧配置下这个场景会一直 502 直到人工 reload。辅助验证：nginx 容器 `/etc/resolv.conf` 的
nameserver 就是 `127.0.0.11`，`nslookup backend 127.0.0.11` → `172.18.0.6`，与实际 IP 一致。

收尾状态：`web=200 / api=200 / 公网 api=200`，占位容器已清理，无残留。

### 推送途中发现的两个真问题

1. **`scp` 覆盖 bind mount 文件会换 inode，容器内不生效。**
   实测宿主机 inode `267033`、容器内 `266374`。因为 scp 用 rename 替换，而 Docker bind mount
   是按 inode 挂载的。对策：改用 `ssh 'cat > 目标' < 源文件`（重定向保持 inode），
   或容器 restart/recreate 重新挂载。
   **顺带验证了 CI 路径是安全的**：`tar xzf` 解压**保持 inode**（实测 `398076 → 398076`），
   所以 `deploy.sh` 里的 reload 确实能读到新文件 —— 这条不成立的话整个修复方案就要推翻。
2. **服务器上从来没有 `rollback.sh`。**
   `.github/workflows/deploy.yml` 的打包清单只有
   `Dockerfile AgentDockerfile docker-compose.yml nginx.conf schema-init.sql deploy.sh`，
   **不含 `rollback.sh`** ⇒ README 里写的 `bash rollback.sh` 实际跑不了。
   本次已手动推送补上。**建议把 `rollback.sh` 加入打包清单**（待负责人确认，属范围外改动）。

## 5. `rollback.sh` 打包缺口（负责人已授权，2026-10-16 21:59 完成）

- `.github/workflows/deploy.yml` 打包清单加入 `deploy/rollback.sh`，并写了注释说明漏掉的后果。
- `agentDeployment.test.mjs` 补断言 `CI must ship the rollback script to the server`。
- 变异检验 5（把它移出打包清单）红 → 恢复后绿。

**途中踩到一个自己的坑**：断言最初写成 `/cp deploy\/.*deploy\/rollback\.sh bundle\//`，
而 `cp` 命令在 YAML 里跨两行、`.` 不匹配换行 ⇒ 恢复后仍红（差点误判成"文件没恢复"）。
改为只匹配 `/deploy\/rollback\.sh bundle\//` 后正常。**跨行文本断言不能用 `.` 通配。**

## 6. 待负责人处理

- 将来升级 runner 镜像（24.04 → 26.04）前，先单独验证
  `docker compose config --format json` 的输出结构未变，再放开 pin（见 workflow 里的注释）。
- 页面手测（我无法代做）：访问 `http://8.137.56.125:8080` 登录后发一条 `/aiAssistant` 问题，
  确认流式回答与历史记录正常。当前接口层已验证 `api=200`。
