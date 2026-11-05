# site-access-and-filing Delta

## ADDED Requirements

### Requirement: 备案域名的默认 HTTP 访问由单一端口变量控制

备案通过后，站点 MUST 能通过不带端口的 `http://<已备案域名>/` 访问。宿主机对外端口 MUST 由 `/opt/cloudwiki/.env` 的 `HTTP_PORT` 唯一决定，且该值 MUST 由 CI 生成 `.env` 时写入 `80`。容器内 Nginx 的监听端口 MUST 保持 `8080`，Compose 端口映射 MUST 保持 `<宿主机端口>:8080` 形式，MUST NOT 写成 `80:80`。

#### Scenario: CI 生成部署用 .env

- **WHEN** GitHub Actions 执行部署打包步骤
- **THEN** 生成的 `bundle/.env` 含 `HTTP_PORT=80` 这一行
- **AND** `HTTP_PORT` 仍出现在 `.env` 必填项自检列表中

#### Scenario: 部署后访问默认端口

- **GIVEN** 安全组已放行 TCP 80 且容器已按新 `.env` 重建
- **WHEN** 用户访问 `http://cloudwiki.club/`（不带端口）
- **THEN** 请求经宿主机 80 转发到容器内 8080 并返回首页 200
- **AND** `/api/user/get/login` 返回非 000/502/503

#### Scenario: 容器端口不被误改

- **WHEN** 检查 `deploy/nginx.conf` 与 `deploy/docker-compose.yml`
- **THEN** `nginx.conf` 仍为 `listen 8080;`，compose 仍为 `"${HTTP_PORT:-8080}:8080"`
- **AND** 部署文档不包含 `"80:80"` 的指引

### Requirement: 首页底部展示完整 ICP 备案号并链接工信部

全局 footer MUST 展示备案系统下发的**完整**网站备案号（含 `-N` 序号），并 MUST 链接到 `http://beian.miit.gov.cn/`。备案号文本 MUST 与备案系统返回的字面量一致。

#### Scenario: 用户查看主应用页面底部

- **WHEN** 用户打开使用 `BasicLayout` 的页面
- **THEN** 底部居中区域显示 `渝ICP备2026028042号-1`
- **AND** 该文本是指向 `http://beian.miit.gov.cn/` 的可点击链接

#### Scenario: 原有页脚内容不被破坏

- **WHEN** 用户查看底部
- **THEN** 原有的「项目文件」链接仍然可见且指向原地址

### Requirement: 端口切换顺序在部署文档中被显式记录

部署文档 MUST 写明「先在安全组放行 TCP 80，再合并触发 CI 部署」的顺序，并 MUST NOT 给出未经验证的 HTTPS 步骤作为可直接执行的操作。

#### Scenario: 运维按文档切换端口

- **WHEN** 运维阅读 `deploy/README.md` 的备案后切换章节
- **THEN** 第一步是安全组放行 TCP 80
- **AND** 文档说明 CI 会重写 `.env`，因此只改服务器 `.env` 不是永久修复
- **AND** HTTPS 章节标注为另行立项，不含可直接照抄的证书签发命令
