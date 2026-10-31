# 第 1 轮 Fetch 预算补证

Change ID／阶段／任务／轮次：deepseek-web-search／1／1.1／第 1 轮同快照补证  
测试实例：/root/tester_round1  
结论：PASS；补齐原 A2 中 Fetch 三次上限的直接证据。

环境：Windows、Java 17，本机 127.0.0.1 临时 HTTP 模拟服务，虚构 local-test-key，未访问外网。分支 feature/deepseek-web-search开发，前后 HEAD 均为 3c8eff28857d1be51e7dbe26a11f9dad65b2aec7。基线为原第 1 轮的 1f161b265f3b97d421a3834435f85a0dd9a26e5a；此轮未运行基线。OpenSpec 已归档，本次仅核查任务卡指定原实现七文件。

快照：manifest.json 的 SHA256 为 0e0c9afd804e7c8e6798abe96b6bca18e2a727e170b1049214f2169354116b7d。前后以 Get-FileHash -Algorithm SHA256 逐项对比清单中 cloud_agent/ 下七项，均 7/7 匹配；后验每项摘要见 test-1-1-round1-budget-hashes.log。测试后 git status 仅本补证日志、摘要和本报告为新增；未修改原报告或被测代码。

操作与命令：在 tmp/BudgetProbe.java 编写一次性同包 Java 探针，模拟端分别计数 /fetch 和 /search 请求并切换 HTTP 200/500。classpath 从现有 cloud_agent/target/surefire-reports/TEST-com.et.agent.DeepSeekWebResearchTest.xml 的 java.class.path 读取。执行 `javac -encoding UTF-8 -cp <classpath> -d tmp/deepseek-budget-probe-round1 cloud_agent/src/main/java/com/et/agent/DeepSeekWebResearch.java tmp/BudgetProbe.java`，随后 `java -cp "tmp/deepseek-budget-probe-round1;<classpath>" com.et.agent.BudgetProbe`；均退出 0。独立编译被测源码，临时输出目录在 classpath 首位。探针断言失败会抛 AssertionError；详细原始输出见 test-1-1-round1-budget-supplement.log。

预期及实际：
- HTTP 200 时同一 bind 的前 3 次 Fetch 分别成功，累计上游请求 1、2、3；第 4 次返回 budget_exhausted，实际请求仍为 3。全部断言通过。
- Fetch 耗尽后 Search 仍可请求，返回 no_results，Search 请求 1 次，Fetch 保持 3 次。断言通过。
- 同一个对象重新 bind 后 Fetch 成功，累计请求由 3 变 4，表明绑定轮次预算重置。断言通过。
- 新 bind 后返回 HTTP 500：第一次 Fetch 返回 upstream_failure 并实际请求 2 次；第二次返回 upstream_failure 且累计 3 次；第三次返回 budget_exhausted 且仍为 3 次。该轮 Search 仍独立请求 2 次并返回 upstream_failure。全部断言通过。

局限：本探针直接调用每轮 bind，与 AgentLoop 在每次 run 时创建绑定的代码路径结合检查；不模拟真实上游、真实密钥或模型自由生成。原任务卡其他验收保持原第 1 轮报告结论。本次未发现需登记的问题。探针 Java 源码及编译类已清理，模拟 HTTP 服务在 finally 停止，无项目开发服务启动。
