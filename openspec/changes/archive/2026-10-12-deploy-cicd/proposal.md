# Proposal

## Why

项目此前只能通过本机的 `start-dev.ps1` 启动，公网演示依赖 cloudflared 快速隧道，域名每次重启都会变化，且必须开着本机、连着网。现在需要：

1. 一个固定地址的长期线上环境，不再依赖本机运行；
2. 代码推送到 GitHub 后自动更新线上版本（CI/CD）；
3. 成本可控（已选定阿里云 99 元/年 ECS，2核2G）。

同时本机与线上存在系统差异（Windows → Linux），需要把这些差异固化进配置，避免"在我机器上能跑"的问题。

## What Changes

新增一套部署与持续交付配置，不改动任何业务逻辑代码：

- `deploy/Dockerfile`：后端运行镜像（eclipse-temurin:11-jre），只负责运行已构建好的 jar，不在服务器上编译。
- `deploy/docker-compose.yml`：MySQL 9.4 + Redis 7 + 后端 + Nginx 四个服务，仅 Nginx 对外暴露端口。
- `deploy/nginx.conf`：托管前端 `dist`，并把 `/api` 转发到后端 8123（含 WebSocket 与长超时）。
- `deploy/schema-init.sql`：从本地库导出的**纯表结构**（11 张表，零业务数据），容器首次启动时自动建表。
- `deploy/setup-server.sh`：服务器一次性初始化（安装 Docker、配置国内镜像加速器）。
- `deploy/deploy.sh`：服务器上执行的部署/更新与健康检查脚本。
- `.github/workflows/deploy.yml`：推送即构建（后端 jar + 前端 dist）→ 打包 → SSH 上传 → 远程部署。
- `.gitattributes`：强制 shell 脚本与部署文件使用 LF 行尾。
- `deploy/.env.example`：登记线上所需环境变量名称（不含真实值）。

关键约束（已在配置中固化）：

- MySQL 版本锁定 9.4（与本地开发容器一致，避免认证插件与 Connector 兼容差异）。
- 字符集 `utf8mb4`、时区 `Asia/Shanghai`（容器侧）与 JDBC `serverTimezone` 同时指定。
- 2G 内存上限下的资源分配：JVM `-Xmx512m`、MySQL `innodb_buffer_pool_size=128M`。
- 3306 / 6379 / 8123 一律不映射到宿主机，只对内网暴露。
- 腾讯云 COS 已停用：配置提供非空占位兜底值，保证应用能正常启动（图片与附件上传功能不可用，RAG 检索与问答不受影响）。

## Capabilities

### New Capabilities

- `deployment-pipeline`：推送代码后自动构建并更新线上环境，构建失败时不影响线上正在运行的版本。

### Modified Capabilities

无。本次不修改任何既有业务需求。

## Impact

- 新增文件全部位于 `deploy/`、`.github/workflows/`、`.gitattributes`，不触碰业务代码与既有配置。
- 密钥只通过 GitHub Secrets 注入，运行期写入服务器 `/opt/cloudwiki/.env`，不进入版本库。
- 前端沿用同源相对路径（`baseURL` 为空），生产打包后无需任何改动即可配合 Nginx 转发。
