# proxy-startup 开发 1.1 第 1 轮

状态：READY_FOR_TEST，等待独立验收。问题 #15 待验证。

## 事实与范围

GitHub run 41287309602 仅证明代理容器在 nginx -t 前已退出，没有当次容器日志；无法确认当次 CI 退出原因。原脚本没有等待默认 HTTP 服务就绪就覆盖配置，存在启动时序风险；本机修改前原脚本连续六次通过，不能据此断言 CI 根因。

修改 `deployProxy.test.mjs`，新增 `proxyLifecycle.mjs` 和 `deployProxyLifecycle.test.mjs`。保留原代理配置验证、后端地址变化、未重启、路径透传断言。只对当前随机标识下成功创建的容器、网络、临时目录执行清理；代理创建报错时通过本轮唯一标签判定归属。诊断只读取本轮代理状态、退出码及 stdout/stderr 日志，不读取环境变量。未改生产部署配置、CI、服务、数据库、依赖或接口。

## 自检记录

- TDD 红灯：空生命周期实现时 `node --test deployProxyLifecycle.test.mjs` 退出 1，六项因等待/拒绝行为缺失失败；清理测试初始空实现时退出 1，清理调用缺失断言失败。
- `node --test deployProxyLifecycle.test.mjs`：退出 0，7 项通过，覆盖延迟就绪、容器退出、超时、配置失败、reload 失败、诊断失败保留原异常、按已记录资源清理。
- `node deployProxy.test.mjs`：退出 0，真实本机 Docker、nginx:1.27-alpine；输出 `Backend replacement proxy test: passed (backend 172.21.0.2 -> 172.21.0.4, proxy never restarted)`；未连接生产服务。
- `git diff --check`：退出 0。范围审查：生产配置、workflow 与依赖未修改。

## 限制与回滚

本机 Windows Docker 环境与 GitHub Ubuntu 不同，此结果不能证明 CI 当次失败的原因或保证不会再次发生。受控测试通过函数注入模拟 Docker 命令；真实 nginx 路径另外在本机执行，未重跑 CI 或部署。只以开发自检报告，不等同独立验收。回滚时恢复 `deployProxy.test.mjs` 并移除本次新增的生命周期帮助文件和测试文件；无需修改运行服务和用户数据。独立测试期间停止修改被测文件。
