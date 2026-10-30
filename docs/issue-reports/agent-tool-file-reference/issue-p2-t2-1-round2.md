# 2.1 第2轮独立验收及符号链接补证

Change ID：`agent-tool-file-reference`；阶段 2／任务 2.1／第 2 轮。Windows 测试实例 `/root/tester_2_1_round2`，2026-10-30 约 09:55 +08:00；补证实例 `/root/tester_2_1_symlink_supplement`。分支 `feature/agent-tool-file-reference开发`，HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。未运行基线或变异检验，未启停服务；并发范围为单进程本地持久卷。

本轮 Windows 原始结论：A1、A3、B1 **PASS**；A2 因普通符号链接测试跳过为**证据不足**，总体证据不足。随后独立实例在 Linux 实际符号链接补证 **PASS**；该补证覆盖普通符号链接，但不能声称 Windows 普通符号链接已实际测试。综合两实例结果供总管判定。

## 各项验收

- A1 **PASS**：在 `cloud` 运行 `mvn -q "-Dtest=AgentPersonalFileStoreTest,AgentResourceInternalControllerTest" test`，退出 0。存储测试 12 项、失败 0、错误 0、跳过 1；控制器测试 1 项通过。测试核对中英文 UTF-8、create 和 append 大小边界，超限 append 和 overwrite 后内容及文件长度。独立 JVM 使用生成型 Reader 和 `java -Xmx32m`，create 和 overwrite 分别写入 52,428,800 字节成功；overwrite 多 1 字节拒绝，文件完整 SHA256 前后相等。独立测试未使用真实用户数据。
- A2 **原始证据不足，Linux 补证 PASS**：指定测试核对用户隔离、相对路径越界拒绝、注入 Reader 异常及提交异常时原文件保持且临时文件清理。`refusesExistingSymlinkAsParent` 因 `symlinks unavailable` 跳过。补证实例报告：同一 29 文件快照无差异；Windows javac 17 编译冻结真实源码和临时注解桩，在禁网络的本地 Docker Java 21 Linux 镜像中运行；用户根、子目录和最终文件的真实 symlink 指向外部时，读取、覆盖、创建及最终文件追加均拒绝，外部哨兵不变，普通路径正常。补证探针源码位于 `%TEMP%/atfr-symlink-probe-fe6ac5782ee34712b15b290d73e3b2f4/SymlinkProbe.java`，命令与结果来自该实例最终报告；未定位独立原始输出日志。Linux 证据不能替代 Windows 普通符号链接的实测。
- A3 **PASS**：指定测试以同一版本启动两个竞争写，断言恰好一个成功且最终文件内容正确；一个文件写入阻塞期间其他文件可写，并测试无版本调用和 ABA 后旧版本冲突。
- B1 **PASS**：独立 JVM 实际创建用户目录 junction 及子目录 junction，分别调用 `write(8, "escape.txt", "x", "create")` 和 `write(7, "link/escape.txt", "x", "create")`，均抛 `IllegalArgumentException: Redirected paths are not allowed`，外部 `escape.txt` 均不存在。指定测试亦检查 junction 下读取和覆盖被拒及原字节保持。故第 1 轮问题 `ATFR-P2-T2.1-R1-001` 更新为已验证修复。

## 快照和开发记录

测试前后逐文件计算清单中的 29 个 cloud、cloud_agent、cloud_front 修改和新增文件 SHA256，均无差异；前后 `git status --porcelain=v1` 范围无差异。清单 SHA256 `01def25722f92e0002afd4b0908fd742389fa94bec29cc7f1768b9520fea1dcc`；开发记录 `bb2c9e8a6d48a7b3b1abb835f104fe8497ba9ede5b6afffbd1d5214d6a0d073a`；累计 diff `dcacf84ff7536c03d804e268d2f5dfeedbc497b728cf09a515c59775f485c7bb`；任务 diff `dc84d04216380671316421991ef3605f53ec1a4418b93e0c612ab508b63087ee`。开发记录列出的三个任务文件哈希匹配清单；测试独立核对其关于 junction、故障注入及指定命令的事实，未把开发自检当作验收。Surefire 报告位于 `cloud/target/surefire-reports/`，可核对结果和跳过原因。

固定验收缺陷：本轮 junction 已修复验证；Windows 普通符号链接实测仍受权限限制。清单外重大回归：本轮未发现。风格意见：无。未修改代码或真实用户文件。

两个实例的临时目录清理均遭自动审批拒绝，未绕过。Windows 探针残留 `%TEMP%/atfr-independent-509151312007460fbd28605558616fe1` 和 `%TEMP%/atfr-tester-r2-status-before.txt`；Linux 补证残留 `%TEMP%/atfr-symlink-probe-fe6ac5782ee34712b15b290d73e3b2f4`。工作簿处理还使用 `%TEMP%/atfr-sheet-r2`；未尝试绕过此前清理拒绝。