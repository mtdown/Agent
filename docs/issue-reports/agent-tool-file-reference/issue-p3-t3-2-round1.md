# 3.2 第1轮独立验收及补证

Change `agent-tool-file-reference`；阶段 3／任务 3.2／第 1 轮；原测试实例 `/root/tester_3_2_round1`，2026-10-30 13:29–13:34 +08:00；补证实例 `/root/tester_3_2_supplement`、`/root/tester_3_2_http`。原总体结论：证据不足；A1、A2 证据不足，A3 已执行部分通过，未发现已证实 FAIL。以下保留原结论并记录补证。综合结论仍为证据不足：真实认证失效以及真实 Agent→Cloud→数据库→浏览器链路未实测。

## 固定验收与实际结果

- A1：原结论证据不足。`cloud_front` 下 `npm run build` 和 `node --test *.test.mjs` 退出 0，后者 98 项通过；`cloud` 下 `mvn -q "-Dtest=AgentPersonalFileReferenceLocalIT,AgentPersonalFilePreviewTest,AgentWikiMultiResultLocalIT" test` 退出 0，14 项通过。受控浏览器连接随机端口 fixture 的模拟 API，历史两轮引用分别展示文件名和字节 6–12；点击第一项，在右侧显示相应位置和中文，上一段与下一段显示不同位置和中文；模拟 SSE 新回合显示并可点击新引用。此页面模拟不能证明真实链路。补证实例报告使用冻结存储源码直接编译、24 MiB 堆、真实 50 MiB 文件验证中文和 emoji 定位及前后段连续，源码见 [large 探针](evidence-p3-t3-2-round1-large.java)，SHA256 `82c3298c65c1b1bb5afbea2e04ec45ced2d35d273cc781c0feb4f050211fdb44`；运行输出仅在补证实例工具记录，未有独立日志，本实例未重跑。此补证仅为存储层，真实端到端 50 MiB 未测。
- A2：原结论证据不足。数据库事务 IT 验证跨用户、错误消息、伪造游标及文件变化拒绝，删除后 resolve 拒绝；页面模拟过期后清空正文并展示“引用不可用、权限已失效或文件已变化，请重新获取引用。” HTTP 补证采用实际预览控制器及生产异常处理器，认证和消息存储是替身。有效引用读取成功；无会话头、跨用户、伪造 token/游标、文件修改和删除均返回非零业务码及 null data，探针对合成正文、路径、私有令牌等进行不泄露断言。源码 [HTTP 探针](evidence-p3-t3-2-round1-http.java) SHA256 `0f31d71ba92b5ba73b8b265a9f51bf7c8adf0cef549ffca6ee20696291488f67`；原日志 `%TEMP%/agent-http-probe-32/run.log` SHA256 `91035ba6e9409197f1f019db8f279ee06d165926da94895c6dc374f934600253`，日志输出因控制台编码显示乱码，源码断言及退出结果为依据。其余拒绝场景业务码为 50000，无会话为 40100。页面 `ragController.ts:290` 将非零码转为异常，`AiAssistantPage.vue:983` 的 catch 清空正文、禁用翻页并显示通用提示；40100 另触发全局登录跳转。此为代码路径推断及模拟页面观察，未实测真实登出或真实文件级撤权。
- A3：已执行部分通过。模拟 RAG 与 Wiki 文档点击显示对应预览，后端 Wiki 回归通过。状态清单未见依赖及数据库迁移改动；代码设 16384 字节上限，存储通道按段读取。真实端到端回归未执行。最新 `devScriptReliability.test.mjs` 明确检查 backendPort、agentPort、frontPort 和失败 `exit 1`，不只是测试数量对比。

## 快照与开发记录

分支 `feature/agent-tool-file-reference开发`；HEAD `7ffc2d629a1d91b46fc8fce3f0ee19da30a3d6f6`。`%TEMP%/agent-tool-file-reference-3.2-round1-sha256.txt` SHA256 `7d0b64f9d84f725ee7447bfaa4908e2f67249dc64065f855bd17087acb72b918`，含完整 43 文件；逐项 `Get-FileHash` 测试前后均无不匹配。完整差异 SHA256 `1e6b462b6f907bae1dc2f0748820bf04a19677e64941759da481515b95262b0a`。分支、HEAD、状态范围不变。开发记录 `tmp/p3t32-development-record.md` SHA256 `e25c8e813ed81cef16bbcf9bc1ab0950851b735d9daa7816bbc886964d152fa3`，记录包括先前失败、重测及模拟页面限制；本实例独立重跑上述命令和页面步骤，不能以开发自检替代验收。两补证实例报告前后 43 文件一致。

## 问题及环境

固定验收缺陷：无已证实缺陷。清单外交付阻断问题：无已证实问题。待追踪证据不足 `ATFR-P3-T3.2-R1-S1`：真实认证失效/文件级权限撤销及完整链路未测。项目未见独立文件级撤权模型；不可将无会话头替身等同真实登出。基线和变异检验未做；未更改被测源。fixture 随机端口 6322 已停止、监听已消失，浏览器标签已关闭；构建产物保留，数据库测试事务回滚。补证临时产物与服务清理由补证实例报告；未冒用用户数据。风格意见无。
