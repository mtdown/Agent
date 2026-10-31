# 独立测试报告

Change ID／阶段／原子任务／轮次：deepseek-web-search／1／1.1／第 1 轮  
时间：2026-10-31 18:47（Asia/Shanghai）  
测试实例标识：/root/tester_round1  
总体结论：PASS

环境与基线：Windows，Java 17，Maven 使用任务卡指定的 IntelliJ 内置路径；分支 feature/deepseek-web-search开发，HEAD 1f161b265f3b97d421a3834435f85a0dd9a26e5a。使用测试内 127.0.0.1 模拟服务器，未使用真实密钥或外网 API；基线未单独运行，未断言基线行为。

被测范围与快照：以 tmp/deepseek-web-search-round1/manifest.json 为 13 个完整变更及记录文件清单，清单 SHA256 为 0e0c9afd804e7c8e6798abe96b6bca18e2a727e170b1049214f2169354116b7d；complete-diff.txt SHA256 为 724ff0133ded0451e94de31e68ae499fed447125043046d54a1b0030b5336910。前后以 Get-FileHash -Algorithm SHA256 逐项对比清单，均 13/13 一致，后验结果见 test-1-1-round1-hashes.log；前后 git status --short --untracked-files=all 与清单一致，后验额外文件均为本轮获准报告和证据。无测试期间并发写入迹象。

开发过程及修改事实记录：developer-1-1-round1.md 的 SHA256 为 1e67fe4539f026feeb46a096e54d241ef95ced1f5026f62ddb656b4adc48d726，匹配任务卡；对照完整 diff，新增及修改文件、配置和回滚描述与当前内容相符，无 pom、数据库迁移或公开 HTTP 控制器签名改动。自检命令本轮独立重跑结果一致；测试先行历史及开发时未调用外网无法独立追溯，不作为验收通过依据。

验收项：

- A1：PASS。命令 mvn -f cloud_agent/pom.xml "-Dtest=DeepSeekWebResearchTest" test，退出码 0，4 tests、0 failures/errors/skipped。searchAndFetchContractAndBounds 模拟服务按 UTF-8 解码请求并断言中文搜索词、count、need_content 和 Bearer，断言搜索标题摘要、Fetch URL、标题和 Markdown、空结果、无效参数不增加请求数、正文长度及脱敏。集成测试另断言搜索结果 URL 到达模型。证据 test-1-1-round1-target.log 和 DeepSeekWebResearchTest.java。
- A2：PASS。同一命令、退出码 0。errorsRetriesBudgetsAndIsolation 断言 401/402/403/404 各一次请求和类别、429/500 各两次、超时两次和类别、搜索总预算三次、独立 Fetch 预算、重新绑定预算隔离及错误脱敏；正文测试亦断言密钥脱敏。证据同上。
- A3：PASS。同一命令、退出码 0。可控模型断言网页工具与已有 get_skill 可用、系统提示包含不可信内容/仅摘要/不猜测，搜索来源和摘要及 Fetch 正文状态进入模型；失败 Fetch 与先前摘要同时存在；空密钥时不声明网页工具且 get_skill 调用结果到达模型。只验证工具调用链和提示，不保证自由生成内容。证据同上。
- A4：PASS。命令 mvn -f cloud_agent/pom.xml test，退出码 0，82 tests、0 failures/errors/skipped，日志 test-1-1-round1-full.log。README-web-research.md 明确独立 DEEPSEEK_WEB_API_KEY、可能产生费用、清空或移除并重启停用；完整 diff 不含依赖、迁移或公开 HTTP API 签名变化。

固定验收缺陷：无。清单外交付阻断问题：无。未验证项：未真实调用上游，符合任务卡权限；未验证开发记录的历史过程。变异检验：未执行，任务卡未授权。问题记录：无。临时文件及服务：测试自身服务器由各测试 AfterEach 停止，无额外临时脚本；保留 Maven target 和本轮报告、日志。风格意见：无。
