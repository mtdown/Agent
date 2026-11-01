# 测试报告

Change ID／阶段／原子任务／轮次／时间：agent-wiki-workflows／2／2.2／1／2026-10-31（北京时间）
测试实例：/root/test_conversation_r1（独立派发，共享工作区）
总体结论：BLOCKED；另有证据不足。无已发现的固定验收 FAIL。

环境与基线：Windows，Java 17，IDEA Maven 3.9.6；分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046。未执行基线。全量测试未在宿主执行，安全隔离环境未建立，不能引用试运行结果作为验收。

被测范围：任务卡 manifest.json SHA256 5190c746e4894b0263124c0a038ca0f1cc725c94bd2c288e1115229df18aee7e；complete-diff.txt SHA256 029481b507d599a4c88de85a7bab2ad3a1aa76f2380ee34bc4952c09c110cd8a；开发记录 SHA256 b30f7a3d9b4c2a5defadfe1b4bae2a1963ea569ab496c5dc4b38bd36d8d09fda。测试前后逐项使用 Get-FileHash SHA256 核验清单，均无不匹配；git status --short --untracked-files=all 前后比较除本轮获准日志及报告外一致。清单包含全部已修改及未跟踪项，未见删除和重命名。测试期间未观察到并发写入。

开发记录核验：对照 git diff 和新增 AgentConversationInitializer，原初始化顺序、SQL、异常处理、标题修复调用从 Store 搬迁到 initializer；Store 保留 PostConstruct 调用。原映射测试文件相对基线无差异；Store 测试新增两个 mock 列检查。开发者报告真实数据库升级与重开未验证的限制符合本次所见。未独立复现修复前失败或核实未接触用户数据的历史事实。

D1：证据不足。执行离线 Maven 指定 AgentConversationPersistenceMappingTest,AgentConversationStoreTest，另用自建无凭据 settings 和临时 Maven 仓库副本；退出 0，7 项、0 失败／错误／跳过。原反射断言核验实体表名、mapper 类型和 Store 字段无 JdbcTemplate；mock 检查缺 interactionJson 时 ALTER、已有 choiceConsumed 时不 ALTER，以及创建、改名和读取归属检查。真实数据库初始化、旧字段升级和历史数据使用未由这些断言验证。日志 test-2-2-r1-d1.log；脱敏本机网络标识，保留原始其他输出，编译器本地编码警告原样保留。

D2 定向：证据不足。离线 Maven 指定 AgentConversationStoreTest,AgentPreviewControllerTest,AgentPreviewProxyWikiEventTest，退出 0，13 项、0 失败／错误／跳过。检查登录、权限过滤、mock 会话归属及本地回环事件投影；未证明真实历史消息投影与 Wiki 写入后会话重开。日志 test-2-2-r1-d2.log，同样脱敏本机网络标识。

D2 全量：BLOCKED，未运行，故无法报告全量跳过项。隔离环境创建命令被自动执行策略拒绝，工具未提供更具体原因。随后在收到总管停止重试指示之前，拆分创建了一个 --network none、标签 wiki-test22=independent 的容器；完整 ID 0b71ee6bdbce52a410664ea224f4e15ef4927ea32c22fbc927b10b01dddaad14，核验为 created、未启动后以该完整 ID docker rm -v 清理。该容器未执行测试、未连接数据库。此拆分尝试不构成隔离环境证明。没有启动 MySQL、Redis 或服务；未执行宿主全量。

固定验收缺陷：无已证实 FAIL。清单外交付阻断：无额外发现。问题记录：#3 待验证，映射定向已通过但全量未完成；#5 待处理；#6 证据不足（真实旧库升级、历史投影及写后重开）。未做变异检验，任务卡未授权。

清理：测试独有容器已删除；无新建网络。临时 Maven 缓存副本及原始日志位于 C:/Users/origin/AppData/Local/Temp/wiki-accept22-67c55190777c452c801b4eff2e5f6054；删除命令被自动执行策略拒绝，保留该目录，未重试。日志包含本机网络标识，勿分享未脱敏副本。Maven target 为获准产物。风格意见：无。
