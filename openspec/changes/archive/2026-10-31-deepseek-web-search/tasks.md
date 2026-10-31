# Tasks

## 1. 网页研究工具

- [x] 1.1 开发者实现 Search、Fetch、每轮预算、模型指引和配置文档，并编写可重复的模拟上游测试；验收 A1–A4 及独立测试通过后勾选。
- [x] 1.2 总管核对独立测试报告、范围与证据，验收通过后同步主规格并严格校验；验证 `openspec validate --specs` 和变更规格一致性。任务完成后另行归档并核对归档结果。

固定 acceptance（工作目录仓库根目录，JDK 11 与 Maven 可用；测试不得调用付费上游）：

A1. Search/Fetch 契约：模拟 HTTP 服务验证 UTF-8 请求、Bearer 鉴权、Search 词及结果和 Fetch URL、标题与 Markdown，空结果可识别，参数无效不发请求，返回内容有界。
    verify：`mvn -f cloud_agent/pom.xml "-Dtest=DeepSeekWebResearchTest" test`
    预期：退出码 0；测试断言上述请求与响应及边界。

A2. 失败和预算：模拟 401、402、403、404、429、500、超时，验证错误类别、非临时 4xx 不重试、临时错误最多重试一次、重试计入每轮每工具三次预算、独立轮次隔离且密钥不泄露。
    verify：`mvn -f cloud_agent/pom.xml "-Dtest=DeepSeekWebResearchTest" test`
    预期：退出码 0；测试断言实际请求次数、隔离与脱敏。

A3. Agent 集成：以可控模型和模拟服务验证已配置时声明并执行搜索、按需抓取，来源和摘要/正文状态到达模型，提示要求外部内容不可信、缺正文仅依据摘要且不猜测；未配置密钥时现有工具仍工作且不声明网页工具。
    verify：`mvn -f cloud_agent/pom.xml "-Dtest=DeepSeekWebResearchTest" test`
    预期：退出码 0；测试断言完整调用链与提示，不以模型自由生成结果宣称可保证。

A4. 回归：运行 cloud_agent 全量单元测试；核对配置文档包含独立密钥、费用和停用方式，无新增依赖、迁移或公开 HTTP API 签名变化。
    verify：`mvn -f cloud_agent/pom.xml test`
    预期：退出码 0、全部测试通过；diff 与配置文档符合范围。

第 1 轮独立验收 A1–A4 PASS：`docs/issue-reports/deepseek-web-search/test-1-1-round1.md` 及其日志；无问题编号。主规格与 delta 一致，新规格严格校验通过，`openspec validate --specs` 32 项通过；其他既有规格存在三项 Purpose 占位警告。问题当前状态以本机问题库为准。回滚：移除 `DEEPSEEK_WEB_API_KEY` 停用功能；需要完全回滚时撤销本变更提交，不涉及数据迁移。
