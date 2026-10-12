# 部署方法

## 一句话

代码推送到 GitHub → GitHub Actions 自动构建（后端 jar + 前端 dist）→ SSH 上传到阿里云 ECS → Docker Compose 重启容器。全程无人值守，本机不再需要跑任何东西。

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
    └──► Redis 7 容器（session + Sa-Token，设密码）

对外只暴露 8080（备案后改 80/443）。
3306 / 6379 / 8123 一律不映射到宿主机，数据库与缓存不暴露公网。
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
| `DOC_USERNAME` / `DOC_PASSWORD` | knife4j 接口文档口令（密码必填） |

**3. 推送代码**

推到 `feature/deploy-cicd开发` 或 `main` 即触发 Actions。

## 日常更新

```bash
git add -A && git commit -m "..." && git push
```

约 5–8 分钟后线上更新完毕。构建失败时 Actions 直接红灯，不会触碰线上正在跑的版本。

## 服务器上的目录

```
/opt/cloudwiki/
├── app.jar            当前后端（app.jar.bak 是上一版，用于回滚）
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

把 `app.jar.bak` 换回并重启后端容器，数据不受影响。

## 排障

| 现象 | 处理 |
|---|---|
| Actions 红灯 | 看 Actions 日志：通常是 maven/npm 构建失败 |
| 首页打不开 | 服务器上 `docker compose logs --tail=200 backend` |
| 后端反复重启 | 多半是 Redis/MySQL 密码不一致，或某个必填环境变量为空 |
| 拉镜像超时 | 确认 `/etc/docker/daemon.json` 里的加速器已生效，`systemctl restart docker` |
| 中文乱码 | 确认 MySQL 启动参数里的 `utf8mb4` 已生效 |
| 时间差 8 小时 | 容器已设 `TZ=Asia/Shanghai`，若仍异常检查宿主机 `timedatectl` |

## 备案通过后切 80/443

1. 改 `/opt/cloudwiki/.env`：`HTTP_PORT=80`
2. 改 `deploy/docker-compose.yml` 的 ports 为 `"80:80"` 并追加 `"443:443"`
3. 在服务器上执行 `certbot --nginx` 申请免费证书（自动续期）
4. `cd /opt/cloudwiki && bash deploy.sh`
