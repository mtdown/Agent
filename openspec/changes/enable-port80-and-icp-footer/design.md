# Design — enable-port80-and-icp-footer

## 1. 取舍：为什么只改宿主机端口，不改容器内 listen

| 方案 | 改动 | 判定 |
|---|---|---|
| A. 只把 CI 生成的 `HTTP_PORT` 改成 80 | 1 行代码 + 注释与文档 | 采纳 |
| B. 把 compose 改成 `"80:80"`，同时把 `nginx.conf` 改成 `listen 80` | 2 处联动 | 否 |
| C. 加 443 server 块并申请证书 | 多文件、需证书挂载 | 本次不做 |

A 的依据：`deploy/docker-compose.yml:125` 已是 `"${HTTP_PORT:-8080}:8080"`，`deploy/nginx.conf:4` 固定 `listen 8080`。把宿主机侧改成 80 后映射为 `80 → 容器 8080`，正好命中监听端口，**不需要动 Nginx 配置**，也不会触发 bind mount 的 inode / reload 问题。

B 是 `deploy/README.md` 现有文字，但只写了改 compose、没写改 `nginx.conf`，照做会形成 `80 → 容器 80`（无人监听）→ 连接拒绝。即便两处都改，也只是把同一个事实换成另一种写法，无收益且增加一处必须同步的耦合。

## 2. 单一配置来源与 CI 覆盖问题

`deploy.sh:57` 与 `rollback.sh` 都从 `/opt/cloudwiki/.env` 读 `HTTP_PORT` 做健康检查；`.env` 由 `.github/workflows/deploy.yml:95-107` 的 `printf` 块**每次部署重新生成**。因此：

- 只改服务器上的 `.env` 会被下一次 CI 覆盖回 8080 —— **不是永久修复**；
- 永久修复 MUST 落在 `deploy.yml:106`；
- `HTTP_PORT` MUST 保留在 `deploy.yml:111` 的 `for required in` 自检列表（该自检只验「有没有这一行」，不验值，改值不会破坏自检）。

对应项目红线：服务器上的任何变更先走 Git 流程，只有负责人明确要求「直接上服务器 / 紧急恢复」时才手工改，且事后补提交。

## 3. 前端 footer 契约

`cloud_front/src/layouts/BasicLayout.vue` 的 `<a-layout-footer class="footer">` 是全局 `position: fixed; bottom: 0; text-align: center`，天然满足「首页底部中间位置」。新增一个 `<a>`：

- 文本 MUST 是**完整备案号含序号** `渝ICP备2026028042号-1`（备案系统给的是 `-1`，不能省后缀）；
- `href` MUST 是 `http://beian.miit.gov.cn/`（通知原文用 http，不擅自改 https）；
- 加 `target="_blank"` + `rel="noopener noreferrer"`；
- 原有「项目文件」链接保持不变，两项之间用分隔符隔开。

无新增依赖、无接口调用、无状态、无权限判断，纯静态文案。

## 4. 数据 / 权限 / 缓存 / 并发

| 维度 | 结论 |
|---|---|
| 数据 | 无数据库读写、无迁移、无向量索引变化 |
| 权限 | 无鉴权变化；footer 对登录与未登录用户一致可见 |
| 缓存 | 无服务端缓存；前端 dist 由 CI 重新构建，浏览器可能缓存旧 footer，强制刷新即可 |
| 并发 | 无共享状态；端口映射变更发生在容器重建时，由 `deploy.sh` 串行执行 |
| 编码 | 新增中文文本按 UTF-8 写入；`.vue` 与 `.md` 均不经 PowerShell 处理 |

## 5. 部署顺序（必须遵守）

1. 负责人在阿里云 ECS **安全组入方向放行 TCP 80**（`0.0.0.0/0`）。
2. 确认备案信息同步完成（通知建议 7 小时后）。
3. 本分支经 PR 合并 `main`（`deploy.yml` 的 `on.push.branches` 含 `main`）触发 CI，`HTTP_PORT=80` 写入 `.env` 并重建容器。
4. 验证 `http://cloudwiki.club` 返回 200，且 `/api/user/get/login` 非 000/502/503。
5. 确认首页底部备案号可见且链接可点。

顺序颠倒（先合并后放行）会让站点在默认端口上短暂不可用；`http://cloudwiki.club:8080` 是否仍可用取决于安全组是否保留 8080，不作为回滚依赖。

## 6. 风险与未解决项

- 安全组状态本机无法核实，由负责人确认（AC4 已把顺序写入 README）。
- HTTPS / 443 未实施，`deploy/README.md` 的相关说明改为「另行立项」，避免误导。
- 公安备案（开通之日起 30 日内）属负责人线下办理，不纳入本 change。
- 本 change 未建立独立测试实例做验收；验证以静态断言 + `openspec validate --strict` + 前端构建为准，如实列入遗留项。

## 7. 回滚

| 触发条件 | 动作 |
|---|---|
| 合并后 80 不通 | 安全组补放行；仍不通则把 `deploy.yml:106` 改回 `8080` 并重新部署 |
| footer 文案错误 | `git revert` 单文件，重新构建前端 dist |
| 文档描述与实测不符 | 直接修正 `deploy/README.md`，无运行时影响 |
| 需整体撤销 | 本分支不合并 main 即可；已合并则 revert 该 PR |

DNS 解析与服务器 `.env` 在本次改动中未被触碰，回滚不涉及它们。
