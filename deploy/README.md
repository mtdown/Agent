# 部署方法

## 一句话

代码推送到 GitHub → GitHub Actions 自动构建（后端 jar + Agent jar + 前端 dist）→ SSH 上传到阿里云 ECS → Docker Compose 重启容器。全程无人值守，本机不再需要跑任何东西。

## 架构

```
浏览器/手机
    │  http://服务器:8080
    ▼
Nginx 容器（托管前端 dist，转发 /api）
    │  内网
    ▼
后端容器（Spring Boot 8123，context-path=/api）
    │  内网
    ├──► MySQL 9.4 容器（库名 picture，数据卷持久化）
    ├──► Redis 7 容器（session + Sa-Token，设密码）
    └──► Agent 容器（8124，仅容器内网；共享内部密钥）

对外只暴露 8080（备案后改 80/443）。
3306 / 6379 / 8123 / 8124 一律不映射到宿主机，数据库、缓存与 Agent 不暴露公网。
```

## 首次部署（一次性）

**1. 服务器初始化**（装 Docker + 国内镜像加速器）

因为 `get.docker.com` 在国内 ECS 上连不上，用阿里云 Docker CE 源：

```bash
apt-get update
apt-get install -y ca-certificates curl gnupg lsb-release
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable --now docker
```

并写入加速器 `/etc/docker/daemon.json`（否则拉镜像会卡死）：

```json
{
  "registry-mirrors": ["https://docker.m.daocloud.io", "https://hub-mirror.c.163.com"]
}
```

以上两步等价于直接执行 `deploy/setup-server.sh`（但该脚本里的 `get.docker.com` 在国内不通，实际按上面命令走）。

**2. 在 GitHub 仓库填 Secrets**

Settings → Secrets and variables → Actions：

| Secret | 说明 |
|---|---|
| `SSH_PRIVATE_KEY` | ECS 密钥对 `.pem` 全文（含 BEGIN/END 行） |
| `SERVER_HOST` | 公网 IP |
| `SERVER_USER` | root |
| `DB_PASSWORD` / `REDIS_PASSWORD` | 自己设定 |
| `COS_SECRET_ID` / `COS_SECRET_KEY` | 可留空，compose 会兜底占位值 |
| `RAG_EMBEDDING_API_KEY` | DashScope（embedding + rerank 共用） |
| `RAG_LLM_API_KEY` | 回答生成模型 |
| `AGENT_INTERNAL_KEY` | 可选；后端和 Agent 的共享内部密钥。未填时 CI 每次部署生成随机值，不会写入日志或仓库 |
| `DOC_USERNAME` / `DOC_PASSWORD` | knife4j 接口文档口令（密码必填） |

后端与 Agent 的快速模型默认使用 `deepseek-flash`；部署 Compose 也固定传入该模型名。

**3. 推送代码**

推到 `feature/deploy-cicd开发` 或 `main` 即触发 Actions。

## 日常更新

```bash
git add -A && git commit -m "..." && git push
```

推送到 `feature/deploy-cicd开发` 或 `main` 即触发，约 5–8 分钟后线上更新完毕
（**首次部署 10–20 分钟**，因为要装依赖、传 65MB jar、拉 MySQL 镜像）。

构建失败时 Actions 直接红灯，不会触碰线上正在跑的版本；
`app.jar` 在部署前会被备份成 `app.jar.bak`，随时 `bash rollback.sh` 回滚。

想不推送代码就手动重跑一次（比如只改了服务器上的 `.env`）：

```bash
cd /opt/cloudwiki && bash deploy.sh
```

GitHub Actions 页面也支持 `Run workflow` 手动触发（无需新提交）。

## 服务器上的目录

```
/opt/cloudwiki/
├── app.jar            当前后端（app.jar.bak 是上一版，用于回滚）
├── agent.jar          当前 Agent（后续部署保留 agent.jar.bak）
├── AgentDockerfile    Agent 运行镜像定义
├── dist/              前端静态产物
├── Dockerfile
├── docker-compose.yml
├── nginx.conf
├── schema-init.sql    首次启动自动建表（纯结构，无业务数据）
├── deploy.sh          部署 / 健康检查
├── rollback.sh        回滚到上一版
└── .env               由 CI 生成，含真实密钥（不进版本库）
```

MySQL 首次启动且数据卷为空时，会自动执行 `schema-init.sql` 建 11 张表；已有数据卷时不会重复执行。

## 回滚

```bash
cd /opt/cloudwiki && bash rollback.sh
```

恢复上一版后端、Agent jar、Compose 配置**与 `nginx.conf`** 并重启服务，数据卷不受影响。首次升级到 Agent 版本时没有 `agent.jar.bak`；回滚会恢复升级前的 Compose 配置，移除 Agent 容器。

`nginx.conf` 是 bind mount，只把文件换回去不会自动生效，所以 `rollback.sh` 会补一次 `nginx -t` + `nginx -s reload`（校验不过就重建 Nginx 容器）。回滚结束时同时探首页和 `/api/user/get/login`，避免重演"首页 200 但接口 502"的假成功。

## 本地 main 验收与任务分支

`start-main-dev.ps1` 需要先切换到 `main`，因此必须在**干净的工作区**执行。若当前任务分支有已暂存、未暂存或未跟踪的文件，先由负责人决定保留、提交或丢弃；脚本和 Git 都不会替你覆盖这些文件。只看当前分支效果可使用脚本已有的 `-Stay` 参数，但它不是 main 效果验收。

Agent 上线后，负责人需要在 `/aiAssistant` 登录并发送一条问题，确认流式回答和历史记录正常；部署脚本只能检查容器运行，无法替代这项页面验收。

## 排障

| 现象 | 处理 |
|---|---|
| Actions 红灯 | 看 Actions 日志的 `[ERROR]` 行；**"Node 20 弃用警告"是干扰项，不会让 job 失败** |
| Actions 红灯但站点其实是好的 | `deploy.sh` 健康检查只等 300 秒，超时会报红但容器仍在后台启动，**以 `docker compose ps` 为准** |
| 首页能开、接口全 502 | **先别急着看后端日志**。取 `docker compose logs --tail=100 nginx` 里 error 行的 `upstream: "http://172.x.x.x:8123/..."`，与 `docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' cw-backend` 的当前 IP 对比：不一致就是 Nginx 缓存了旧后端地址（见下方"已踩过的坑"第 5 条）；一致再看 `docker compose logs --tail=120 backend` |
| 后端反复重启 + `UnsupportedEncodingException: utf8mb4` | JDBC 的 `characterEncoding` 只认 Java 字符集名，必须写 `UTF-8`（见下方"已踩过的坑"） |
| 后端反复重启（其他原因） | 多半是 Redis/MySQL 密码不一致，或某个必填环境变量为空（如 `DOC_PASSWORD`） |
| 编译报 `cannot find symbol: toList()` 等 | 用了 Java 16+ API，但目标是 Java 11；本机 JDK 17 编得过、CI 的 JDK 11 编不过 |
| 拉镜像超时 | 确认 `/etc/docker/daemon.json` 里的加速器已生效，`systemctl restart docker` |
| 中文乱码 | 确认 MySQL 启动参数里的 `utf8mb4` 已生效 |
| 时间差 8 小时 | 容器已设 `TZ=Asia/Shanghai`，若仍异常检查宿主机 `timedatectl` |

## 已踩过的坑（2026-10-12 首次部署实录）

1. **`characterEncoding=utf8mb4` 会让后端启动失败**。MySQL Connector/J 的这个参数只接受
   **Java 字符集名**（`UTF-8`），写 MySQL 字符集名 `utf8mb4` 会抛 `UnsupportedEncodingException`，
   Spring Boot 启动失败 → 容器崩溃重启 → Nginx 502。服务端字符集由 `--character-set-server=utf8mb4`
   指定，驱动会自动协商，JDBC URL 里写 `UTF-8` 即可。
2. **本机 JDK 17 会掩盖 Java 16+ API 误用**。pom 里只有 `source/target 11` 时，javac 仍按 JDK 17 的
   API 表面编译，`Stream.toList()` 之类本机编得过、CI 的真 JDK 11 才炸。已加 `<release>11</release>`
   作为护栏，本机就能提前暴露。
3. **健康检查只探首页会造成"假成功"**。首页由 Nginx 直接吐静态文件，后端挂了也返回 200。
   现在 `deploy.sh` 同时探 `/` 和 `/api/user/get/login`，后者非 000/502/503 才算后端活着。
4. **首次部署 10–20 分钟属正常**（Maven/npm 无缓存 + 65MB jar 上传 + 拉取 MySQL 镜像）；
   之后每次 5–8 分钟。
5. **Nginx 会把后端容器 IP 缓存到"配置加载那一刻"，后端重建后接口全 502**（2026-10-16 实录）。
   症状极具迷惑性：`cw-nginx` 已运行 4 天、`cw-backend` 刚启动且健康、首页 200、只有 `/api` 全 502。
   根因是 `proxy_pass http://backend:8123` 只在 Nginx 加载配置时解析一次，部署重建后端容器后
   旧 IP 无人监听，`connect() failed (111: Connection refused)`。
   排障手法：**看 nginx 日志里的 upstream IP，和 `cw-backend` 当前 IP 比一比**，不一致即可定性
   （本次是日志里 `172.18.0.4` vs 容器 `172.18.0.6`）。
   - 临时恢复：`docker compose exec -T nginx nginx -s reload`（**不用重启容器、不影响首页**）。
   - 永久修复：`nginx.conf` 用 `resolver 127.0.0.11 valid=10s ipv6=off;` + 变量 upstream
     （`set $backend_upstream "backend:8123";` / `proxy_pass http://$backend_upstream;`），
     让 Nginx 每次请求重新解析；`deploy.sh` 在启动容器后先 `nginx -t` 再 `nginx -s reload`，
     因为 `nginx.conf` 是 bind mount，换文件不自动生效。
   - 回归护栏：`deployProxy.test.mjs` 用真实容器复现"后端换 IP + Nginx 不重启"，
     写死 upstream 的旧配置跑它会失败。

## 首次上线后：建第一个账号

`schema-init.sql` 只建表、**0 条业务数据**，所以线上库是空的。第一件事是走**注册**（不是登录）：

1. 浏览器注册一个账号（默认普通用户角色）
2. 需要管理员时，在服务器上提升角色：

```bash
cd /opt/cloudwiki
source .env
docker compose exec mysql mysql -uroot -p"$DB_PASSWORD" picture \
  -e "UPDATE user SET userRole='admin' WHERE userAccount='你的账号'"
```

## 备案通过后切 80/443

1. 改 `/opt/cloudwiki/.env`：`HTTP_PORT=80`
2. 改 `deploy/docker-compose.yml` 的 ports 为 `"80:80"` 并追加 `"443:443"`
3. 在服务器上执行 `certbot --nginx` 申请免费证书（自动续期）
4. `cd /opt/cloudwiki && bash deploy.sh`
