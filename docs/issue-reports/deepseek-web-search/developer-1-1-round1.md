# 开发过程记录：1.1 第 1 轮

基线：feature/deepseek-web-search开发，HEAD 1f161b265f3b97d421a3834435f85a0dd9a26e5a。OpenSpec apply CLI 状态 ready，任务 1.1 待执行。测试先行：工具声明缺失导致测试失败，增加实现后通过。

修改：新增 DeepSeekWebResearch.java、DeepSeekWebResearchTest.java、README-web-research.md；修改 AgentApplication.java、AgentLoop.java、AgentToolRegistry.java、application.yml。均在 cloud_agent 内；无依赖、数据库或公开 HTTP API 修改。

环境：Java 17，Maven 来自 IntelliJ IDEA 2024.1 内置 Maven，bin 前置 PATH。模拟服务使用本机 127.0.0.1，未调用付费上游。

自检：`mvn -f cloud_agent/pom.xml "-Dtest=DeepSeekWebResearchTest" test` 退出码 0，4 tests，0 failures/errors；`mvn -f cloud_agent/pom.xml test` 退出码 0，82 tests，0 failures/errors；`git diff --check` 退出码 0。模拟请求覆盖 Bearer、UTF-8、参数、响应、失败及预算，模型注入测试验证数据流和提示。结果为开发自检，不代表独立验收。

回滚：移除或清空 DEEPSEEK_WEB_API_KEY 并重启以停用；完全回滚撤销本次文件修改，无数据迁移。
