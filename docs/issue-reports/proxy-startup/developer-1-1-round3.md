# proxy-startup 开发 1.1 第 3 轮

根据 [第 2 轮只读复核](review-1-1-round2.md) 处理 #18 和 #19，原固定 A1–A4 不变。未修改生产配置、CI workflow、依赖、接口、服务或数据。

## 修改

- CLI 错误输出使用固定白名单分类：代理提前退出、就绪超时、资源创建、nginx 检查、reload、请求及兜底类别；Docker 失败只附 0–255 数值退出码，不打印原始消息、stdout 或 stderr。内部异常对象仍保留；诊断摘要仍只显示有限安全信息。
- 在代理配置完成后和后端替换验证完成后，按同一不可变代理 ID 采集并比较容器 ID、StartedAt 和 RestartCount，检测替换期间重启并恢复；保留原 `.State.Restarting` 检查、后端 IP 变化和原样路径验证。

## 开发自检

- 红灯：先增加错误分类/退出码及重启恢复反例，旧实现对应 2 项失败；完成后通过。
- `node --test deployProxyLifecycle.test.mjs`：退出 0，20 项通过，包括重启后恢复、容器替换、各安全分类及异常不泄露的受控反例。
- `node deployProxy.test.mjs`：退出 0，真实本机 Docker 及 nginx:1.27-alpine，输出 `Backend replacement proxy test: passed (backend 172.21.0.2 -> 172.21.0.4, proxy never restarted)`。
- 真实脚本 CLI 子进程：使用本轮 `tmp/proxy-startup-r3/fake-docker.c` 编译的专用 docker.exe，借 PATH 注入；分别模拟创建失败退出 7、代理提前退出、nginx 检查失败退出 42、就绪超时。四个完整脚本进程均退出 1，分别出现 create/7、proxy-exited、nginx-check/42、proxy-timeout，模拟秘密均未出现。此测试不操作 Docker 资源；模拟文件保留在该本轮目录，不触及先前被策略拒绝清理的目录。reload 和请求分类通过受控单元测试，不声称经过此 CLI 注入。
- `git diff --check`：退出 0；范围检查无生产配置、CI、依赖变更。未重跑 CI 或部署；CI 当次根因未知。

问题 #18、#19 已更新为待验证。自检不是独立验收；完成交接后停止写入，等待第 3 轮独立测试。回滚恢复原脚本并移除生命周期帮助与测试，不改运行服务或用户数据。
