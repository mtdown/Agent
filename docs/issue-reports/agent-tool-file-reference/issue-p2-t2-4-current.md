# 2.4 第 1 轮当前快照独立补证

- Change／阶段／任务／轮次：`agent-tool-file-reference`／2／2.4／第 1 轮当前快照补证；时间：2026-10-30。
- 测试实例：`/root/tester_2_4_current`；总体结论：**PASS**。固定项 A1、A2、A3 均通过；资源并发压测和 2G 峰值按负责人决定暂缓。
- 分支 `feature/agent-tool-file-reference开发`；HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。未切换分支或运行基线。受测清单含 47 个文件，测试前后逐个 SHA256 校验均无差异；清单 SHA256 `65f2d54c09b2eb3cd72985c988066d1ee8b87505407e46a900b3d0472860488f`，完整累计 diff SHA256 `ed586f692a3b1c072bdc76bbc845df945e59ed50670ee3d889ee389fd4a1ad1a`。探针编译输入 555 项在执行前后逐项核对均无差异。测试期间未修改被测源码或真实数据。
- 开发事实记录 [developer-2-4-round1.md](developer-2-4-round1.md) 的 SHA256 为 `5c591909b956ff64c407aee755131dd77f496deff630a822b758efbebcb63ffa`，与任务卡一致；其当前测试结果和配置说明经独立执行与核对支持，早期编译失败过程未独立复现。

## 固定验收

- **A1 PASS**：在 `cloud` 执行 `mvn -q "-Dtest=AgentPersonalFileUploadTest,AgentPersonalFileStoreTest,AgentPersonalFileSegmentTest" test`，退出 0。上传 6 项、存储 12 项（11 通过、1 因 Windows 符号链接权限跳过）、分段 5 项。测试覆盖创建、覆盖、追加与完整 50 MiB 字节。独立真实 HTTP 探针在随机端口 5866 用 50 个 1 MiB 分段提交，实际持久文件的 SHA256 与预期均为 `a27017450ed5f6ac334ffa9be401a5ae1f24465aac9b98a790d0eec6833599d9`；检查匿名和跨用户分段、提交、取消被拒绝及退出后被拒绝。
- **A2 PASS**：指定测试覆盖越限、重复、跳段、不完整、版本冲突、注入提交失败、取消及过期清理。真实 HTTP 检查断线、无效 UTF-8、错误响应的绝对路径脱敏；断线及无效 UTF-8 前后原文件 SHA256 均为 `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`。探针结束后上传临时目录为空。失败响应 HTTP 状态为 200 时依据响应体非零业务码判断拒绝。
- **A3 PASS**：代码检查确认上传与写入使用 8 KiB 缓冲，单段上限 1 MiB；`deploy/nginx.conf` 的 `client_max_body_size 30m` 可容纳单段，`deploy/README.md` 记载临时目录、个人文件卷及回滚方式。完整 50 MiB 实际持久字节及现有存储调用通过测试；未执行总体并发和 2G 峰值压测，不能据此推断资源峰值。

## HTTP 补证边界与证据

探针在隔离临时目录启动随机端口 Tomcat，调用当前源码编译的真实上传控制器、生产 `UserServiceImpl` 会话方法和 `GlobalExceptionHandler`；仅用户查询 `getById` 以受控替身提供用户。fixture 直接设置和移除会话属性，并非完整登录或登出入口的端到端验证。未连接真实数据库、未重启现有服务。

临时证据保留于 `C:\Users\origin\AppData\Local\Temp\agent-current-test-4d69fecee41b4d85893c51666545bc78`：`SessionProbe.java` SHA256 `a7f4c1f9e5689599a67e8cdbfd48e125ee1b265d56ff73a4d941884dfc5c44d1`，`probe2.log` SHA256 `724f03db9a03b88d6b66b1d2dc8c7e51165893ea285dab3c587914f8279df8fd`，`inputs-before2.txt` 保存编译输入摘要。第二次探针单独重新编译、执行退出 0。首次 `probe.log` 打印的 50 MiB 摘要因再次调用 `digest()` 显示空串摘要；该打印值不可作为文件摘要。第二次修正打印后重新验证，实际文件另以 `Get-FileHash` 核对为上述 `a270...`。

固定验收缺陷、清单外交付阻断问题及风格意见：无。变异检验未执行，任务卡未要求。测试后 47 项及 555 项输入均匹配，隔离 Tomcat 已停止，上传临时目录为空；按总管指示保留探针目录。IssueLog 未发现本任务 2.4 对应的既有条目，本轮无须写入。
