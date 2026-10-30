# 2.1 第1轮独立验收记录

实例：`/root/tester_2_1_round1`；2026-10-30，约 09:48 +08:00。总体 **FAIL**。Windows；Java 17；Maven 3.6.1；分支 `feature/agent-tool-file-reference开发`；HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。未运行基线、未启停服务、未做变异检验。

## ATFR-P2-T2.1-R1-001：Windows junction 逃逸（A2，待处理）

前提：用户目录 `data/7/link` 为指向 `outside` 的 Windows junction。用 `New-Item -ItemType Junction` 创建成功。独立 JVM 使用 `new AgentPersonalFileStore(data).write(7, "link/escape.txt", "x", "create")`。预期：拒绝目录逃逸且用户目录外不创建文件。实际：调用成功，`outside/escape.txt` 存在。输出见 [证据](evidence-p2-t2-1-round1.txt)，探针源码见 [Probe.java](evidence-p2-t2-1-round1-probe.java)，[实际与预期对照](issue-p2-t2-1-round1.html)。影响：通过 Windows junction 可写到用户目录外。本测试只操作系统临时目录。

## 验收结果

- A1 **PASS**：`cloud` 下执行 `mvn -q "-Dtest=AgentPersonalFileStoreTest,AgentResourceInternalControllerTest" test`，退出 0；存储测试 11 项、失败 0、错误 0、跳过 1，控制器测试 1 项通过。覆盖三种写法、中英文、大小边界和超限拒绝。独立探针以 `java -Xmx16m` 流式创建 52,428,800 字节文件成功；超限 overwrite 和 append 均拒绝，原文件完整 SHA256 前后均为 `A27017450ED5F6AC334FFA9BE401A5AE1F24465AAC9B98A790D0EEC6833599D9`。
- A2 **FAIL**：用户隔离、路径越界、读入异常及提交异常保留原内容并清理临时文件的既有测试通过；符号链接测试因环境权限跳过。独立 junction 探针发现上述逃逸，不能通过。
- A3 **PASS**：指定测试验证同版本两次竞争仅一个成功、最终内容、其他文件不等待，以及无版本调用。独立两个 JVM 输出同一版本；A→B→A 后旧版本写入被拒绝。只验证单进程协调，未验证跨进程锁。

## 输入及环境核验

测试前后逐项计算冻结清单 29 文件 SHA256，均匹配；前后 git status 范围一致。清单 SHA256 `FFF9A8A44CC3547F984B41A47BFE13645E4722785C82C42B14B2EDB4397D5CBD`；开发记录 `76161E75D705370316A2EDD2F7D7B7890A2B6A9DD3DF1EACAAC85BEB3545CD3C`；累计 diff `9D6029689E2B6CAC8811AF8B6623018156D85119F76357DCE4CE17A985E7E6F5`；任务 diff `68C3AFA1E136D83962064990701B0FB8208FDBE9C09E34B51CABD6DD10E3BC68`。开发记录中的三个任务文件哈希匹配；所述符号链接跳过、Reader 和提交故障注入由本轮独立测试核对。Surefire 报告保存在 `cloud/target/surefire-reports/`，包括符号链接跳过原因。未改业务代码或真实用户文件。

临时目录 `C:\Users\origin\AppData\Local\Temp\atfr-independent-probe` 中 junction、生成的类和约 50 MiB 测试文件未清理：自动审批拒绝了 `Remove-Item` 删除操作，未给出更具体原因；没有绕过拒绝。清单外重大回归：无其他已复现项。风格意见：无。