# 3.1 第1轮独立验收及失权补证

Change `agent-tool-file-reference`；阶段 3／任务 3.1／第 1 轮；原测试实例 `/root/tester_3_1_round1`，2026-10-30 12:54–12:55 +08:00；补证实例 `/root/tester_3_1_revocation`。原结论：A1、A3 PASS，A2 证据不足，原因是未实际验证个人文件失权后的引用解析。补证覆盖登出后会话访问撤销，不能证明文件级权限撤销；是否接受该范围由总管判定。

## 固定验收及命令

```powershell
# cloud
mvn -q "-Dtest=AgentPersonalFileReferenceTest,AgentPersonalFileReferenceLocalIT,AgentWikiMultiResultLocalIT" test
# cloud_agent
mvn -q "-Dtest=AgentToolRegistryTest,AgentLoopTest,AgentControllerWikiEventTest" test
```

两条命令均退出 0；Cloud 15 项、Agent 27 项，失败、错误及跳过均为 0。对应 `target/surefire-reports` 中六个同名文本报告可能被后续运行覆盖。

- A1 PASS：真实数据库事务中，搜索和读取两个真实文件产生三处引用；保存、历史查询及解析断言两处字节位置和另一文件路径。另以真实文件工具证据与受控 Agent SSE 替身贯通 Cloud 代理、持久化和历史解析；AgentLoop 单测验证工具证据退出模型历史并生成内部事件。事务回滚。
- A2 原结论证据不足：数据库测试断言跨用户、跨消息、随机令牌、文件修改和删除后拒绝；单测断言位置篡改签名和跨用户证据拒绝，伪造证据不增加引用。流与历史断言不含文件正文和私有路径。未实际测试个人文件失权；以下补证只证明登出撤销会话访问。
- A3 PASS：Wiki 多结果及历史测试、Agent 循环与错误测试通过；工作区状态无 pom 或迁移改动，开发事实表明本轮未改前端。未做浏览器验证。

## 独立补证与限制

补证实例报告真实 `UserServiceImpl.userLogout` 和真实 Servlet 会话：引用先成功，登出后使用同一 cookie 再请求时抛 `BusinessException`，且没有调用引用解析。用户数据源及消息存储为隔离替身；探针自行把异常映射为 HTTP 401，不能证明项目实际 HTTP 异常响应。源码中并无解析调用计数断言；未调用解析的判断依据是控制器先调用登录用户检查的代码路径。探针源码 [evidence-p3-t3-1-round1-revocation.java](evidence-p3-t3-1-round1-revocation.java) SHA256 `3566ceb17d89d01337bd6c2f8f82f274e2eb1ef2f80f3c5fda2df4d0dfbac12c`，日志 [evidence-p3-t3-1-round1-revocation.log](evidence-p3-t3-1-round1-revocation.log) SHA256 `76fcb8b1c3d1946d5bbc9f5a0cf43fac2b15b498c56c8525ff72d7a97307fd8c`。原测试实例仅核对文件及哈希，未重跑；补证 class 未重新编译，日期晚于源码，不能由日期独立证明二者一致。项目未见单独文件级撤权模型。真实跨进程 Agent→Cloud→数据库和浏览器链路未验证。

## 快照及环境

分支 `feature/agent-tool-file-reference开发`；HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。快照 `%TEMP%/agent-tool-file-reference-3.1-round1-sha256.txt` SHA256 `59c16a76a015556040650984b1d1526c7418ddc22a4d29673b9ca4087e427fb1`；41 文件逐一测试前后均匹配；完整 diff SHA256 `0f27992276a5a531b48f0a355f342c2df77ac2b1172a7f6557a40f392b8723b3`。分支、HEAD、状态范围不变。开发事实 [dev-p3-t3-1-round1.md](dev-p3-t3-1-round1.md) SHA256 `e54fa712b66d519bbce17f036dd87212324be10ada2cdda930fc742f66211f5e`，14 条文件及日志哈希均核对一致。补证实例报告其前后 41 文件匹配。

未运行基线或变异检验；未启动开发服务。测试 HTTP 服务关闭，数据库事务回滚，JUnit 临时目录清理；Maven 构建产物保留。无已确认固定验收缺陷或清单外重大回归。证据不足记录 ATFR-P3-T3.1-R1-S1；补证的临时源码与日志已保存，其他临时产物清理由补证实例报告。