# Tasks: deploy-cicd

## 1. 服务器准备（人工执行一次）

- [x] 1.1 阿里云 ECS（Ubuntu 22.04）安装 Docker CE 与 Compose 插件
      - 验证：`docker --version` → 28.1.1；`docker compose version` → v2.35.1
- [x] 1.2 配置 `/etc/docker/daemon.json` 镜像加速器
      - 国内 ECS 直连 `get.docker.com` 不可达，改用阿里云 Docker CE 源安装
- [x] 1.3 安全组入方向放行部署端口 8080

## 2. 部署配置

- [x] 2.1 `deploy/Dockerfile`：eclipse-temurin:11-jre，只运行已构建的 jar
- [x] 2.2 `deploy/docker-compose.yml`：mysql:9.4 + redis:7-alpine + backend + nginx
- [x] 2.3 `deploy/nginx.conf`：托管 dist，转发 `/api`，含 WebSocket 与 300s 超时
- [x] 2.4 `deploy/schema-init.sql`：纯表结构（11 张表，0 条业务数据），空库自动建表
- [x] 2.5 `deploy/deploy.sh`：校验产物 → 备份 jar → 启动 → 健康检查
- [x] 2.6 `deploy/rollback.sh`：恢复 `app.jar.bak` 并重启后端
- [x] 2.7 `deploy/setup-server.sh` + `deploy/.env.example`
- [x] 2.8 `.gitattributes`：强制 `*.sh` 与 `deploy/**` 为 LF
- [x] 2.9 `deploy/README.md`：部署方法文档（架构、首次部署、Secrets、回滚、排障）

## 3. CI 流水线

- [x] 3.1 `.github/workflows/deploy.yml`：JDK 11 构建 + Node 22 构建 → tar → scp/ssh 部署
- [x] 3.2 升级官方 action 版本（checkout v7 / setup-java v6 / setup-node v7）
- [x] 3.3 增加部署 Secrets 非空校验
- [x] 3.4 SSH 选项兼容阿里云 RSA 密钥对（`HostKeyAlgorithms` / `PubkeyAcceptedAlgorithms`）
- [x] 3.5 部署前 SSH 连通性自检，失败时输出可读排查提示

## 4. 缺陷修复

- [x] 4.1 `RagLlmClient:141` 的 `Stream.toList()`（Java 16+）改为 `Collectors.toList()`
- [x] 4.2 `cloud/pom.xml` 加 `<release>11</release>`，本机 JDK 17 也能暴露 Java 16+ API 误用
- [x] 4.3 JDBC `characterEncoding=utf8mb4` 改为 `UTF-8`（原值导致后端启动失败、接口 502）
- [x] 4.4 `deploy.sh` 健康检查增加后端 API 探活（原来只探首页，后端挂了也判成功）

## 5. 验证结果

自动化验证：

- [x] 5.1 本机 `mvn -B -DskipTests package`（JDK 17 + release 11）BUILD SUCCESS，218 主源码 + 37 测试源码通过
- [x] 5.2 服务器 `docker compose ps` 四个容器均为 Up
- [x] 5.3 服务器 `curl http://127.0.0.1:8080/api/user/get/login` 返回非 502
- [x] 5.4 浏览器打开 `http://<公网IP>:8080/` 前端页面正常加载

人工验收（需负责人执行后回填，本分支不勾选）：

- [ ] 5.5 注册账号并登录成功
- [ ] 5.6 创建/编辑一篇 Wiki 文档并保存
- [ ] 5.7 RAG 检索返回命中结果
- [ ] 5.8 执行 `bash rollback.sh` 能回到上一版并重新起来

## 6. 回滚方案

- 代码回滚：`git revert` 对应提交并推送，CI 会自动重建
- 版本回滚：服务器 `cd /opt/cloudwiki && bash rollback.sh`（换回 `app.jar.bak`，不影响数据）
- 数据回滚：当前**无备份**，见「遗留风险」

## 7. 遗留风险（本 change 范围外）

- 线上数据库无备份，需另行增加定时 `mysqldump`
- 未备案，仅能使用 8080；备案通过后切 80/443 并启用 HTTPS
- 对象存储已停服，图片与附件上传不可用（Wiki 正文与 RAG 检索问答不受影响）
