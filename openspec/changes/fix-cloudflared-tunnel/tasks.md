# fix-cloudflared-tunnel Tasks

## 1. 脚本修复

- [x] 1.1 修改 `start-dev.ps1` Preview 前端构建：stdout / stderr 分别重定向到 `dev-frontend-build.log` 与 `dev-frontend-build.err.log`；验证方式为 PowerShell 5.1 语法解析通过且不再出现"RedirectStandardOutput 和 RedirectStandardError 相同"异常
- [x] 1.2 修改 `start-dev.ps1` 公网地址探测：`Get-TunnelPublicUrl` 接收当前隧道工具参数，工具为 cloudflared 时跳过 9200 / 4040 API 探测，仅从 cloudflared 日志提取 `trycloudflare.com` 地址；验证方式为源码静态检查（cloudflared 分支不请求 9200/4040）

## 2. 记录与验证

- [x] 2.1 在 `IssueLog.xlsx` 追加两行问题记录（重定向同文件异常、cpolar 抢答公网地址），状态置为已修复
- [x] 2.2 语法解析验证：`PSParser::Tokenize` 对修改后的 `start-dev.ps1` 无解析错误
- [x] 2.3 实跑验证：`stop-dev.ps1` 后执行 `start-dev.ps1 -Tunnel cloudflared -Preview -NoPause`，确认前端构建完成、3000/8123 端口监听、控制台打印 `Starting public tunnel via cloudflared` 且 Public URL 为 `*.trycloudflare.com`
- [x] 2.4 向负责人汇报验证结果与人工测试项，等待确认后再执行 `upload.ps1`

## 3. 上传（负责人确认后）

- [x] 3.1 执行 `upload.ps1 -Message "fix(start-dev): preview 构建重定向与 cloudflared 地址探测"` 推送任务分支并提示发起 PR

## 4. 验收反馈（2026-09-12）

- [x] 4.1 负责人确认公网隧道链路可用（手机可访问 trycloudflare 公网地址）
- [ ] 4.2 遗留新问题转后续任务处理：手机端（<760px）无登录入口、不自动跳转登录页（右上角登录态被 mobile.css 隐藏且路由守卫不重定向），已记录 IssueLog，待负责人确认修复方案后另立任务分支

## 回滚方案

还原 `start-dev.ps1` 中 1.1 / 1.2 两处改动（`git revert` 单个提交或手工改回），无数据迁移。
