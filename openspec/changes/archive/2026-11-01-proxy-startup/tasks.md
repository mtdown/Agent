# Tasks

## 1. 代理回归启动及诊断

- [x] 1.1 修复测试启动时序并保留失败诊断；使用受控启动、退出及非法配置测试验证 A1–A3，真实 Nginx 验证 A4。独立证据见 `test-1-3-round1.md`；此前 BLOCKED 的原始记录保留。
- [x] 1.3 根据新的人工清理规则，保留代理测试临时目录并报告准确路径与清理命令，不修改部署 workflow；验证 A5。独立证据见 `test-1-3-round1.md`；目录待管理员处理。
- [x] 1.2 独立验收、核对证据并完成规格同步与严格校验；历史 BLOCKED 原样保留，按新规则记录目录待管理员处理，见 `docs/issue-reports/proxy-startup/manual-cleanup.md`。

## 固定 acceptance

A1. 代理容器启动延迟时，必须等待 HTTP 服务就绪后才覆盖配置；容器退出或等待超时须非零失败。
    verify：`node --test deployProxyLifecycle.test.mjs`
    预期：受控 Docker 替身记录执行顺序、延迟、退出及超时；未就绪时不得复制配置或声称通过。

A2. 启动失败、`nginx -t` 失败和 reload 失败应在清理测试资源前输出可用的状态、退出码及日志；诊断错误不得掩盖原失败，不输出秘密或无关容器信息。
    verify：`node --test deployProxyLifecycle.test.mjs`
    预期：逐项断言非零、原始错误与本次测试容器诊断和清理；不能把失败改为通过。

A3. 代理就绪后按原合同校验交付配置并 reload，测试仅清理本轮随机标识的容器和网络，临时目录交管理员在验收后处理；不改生产配置、部署 workflow、外部接口或依赖。
    verify：`node --test deployProxyLifecycle.test.mjs`；`git diff --check`；范围审查。
    预期：受控测试断言请求顺序和清理范围，diff 无生产配置、workflow 或依赖变更；临时目录不由测试脚本删除。

A4. 真实 Nginx 测试在本机运行 `node deployProxy.test.mjs`，保留配置校验、后端 IP 变化、代理未重启和 `/api` 路径原样透传断言。
    verify：`node deployProxy.test.mjs`
    预期：退出码 0，输出后端更换地址且代理未重启的成功信息；该测试不连接生产服务。

A5. 代理测试执行后保留本轮临时目录，输出准确路径和仅针对该目录的清理命令，交管理员在验收后处理；生产部署 workflow 不变。
    verify：受控测试及本机真实代理回归，检查临时目录仍存在且所给命令仅指向本轮目录；`git diff -- .github/workflows/deploy.yml` 为空。
    预期：测试不删除临时目录，容器及网络仍按归属清理，记录待人工处理且不以此阻止验收。

## 证据与回滚

原始 GitHub run 41287309602 的 job 日志只确认容器在 `nginx -t` 前已退出，没有代理容器日志，根因未知。本机修复前真实回归 6 次通过；不能凭此推断 CI 原因。报告与 issue 记录在 `docs/issue-reports/proxy-startup/`；回滚本次测试文件及规格即可，不改运行服务或用户数据。
